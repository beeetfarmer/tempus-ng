package com.cappielloantonio.tempo.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ActivityMusicVideoPlayerBinding;
import com.cappielloantonio.tempo.popinn.PopinnApi;
import com.cappielloantonio.tempo.popinn.PopinnClient;
import com.cappielloantonio.tempo.popinn.PopinnPlayRequest;
import com.cappielloantonio.tempo.popinn.PopinnSubtitle;
import com.cappielloantonio.tempo.popinn.PopinnVideo;
import com.cappielloantonio.tempo.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Plays one Popinn music video, and nothing else.
 *
 * Deliberately separate from {@link MainActivity} and its media session: it does
 * not touch the music queue and nothing here is scrobbled to Subsonic. It does
 * take audio focus, which pauses whatever music was playing, and it reports
 * watch time back to Popinn so its own view counts stay accurate.
 */
@OptIn(markerClass = UnstableApi.class)
public class MusicVideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = "MusicVideoPlayer";

    /** Seconds moved per double tap, accumulating while taps keep coming. */
    private static final int SEEK_STEP_SECONDS = 10;
    private static final long SEEK_FEEDBACK_TIMEOUT_MS = 800;

    private ActivityMusicVideoPlayerBinding bind;
    private ExoPlayer player;
    private PopinnVideo video;

    private long resumePosition = C.TIME_UNSET;

    private final Handler seekFeedbackHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideSeekFeedback = this::hideSeekFeedback;
    private int accumulatedSeekSeconds;
    private boolean lastSeekWasForward;

    /**
     * Wall-clock time spent actually playing, accumulated across pauses and any
     * trip through the background. Measured this way rather than from the
     * playhead so that seeking backwards and rewatching a section cannot
     * overstate it, and skipping forward cannot claim credit for what was
     * never on screen.
     */
    private long watchedMs;
    private long watchStartedAtMs = C.TIME_UNSET;
    private int knownDurationSeconds;
    private boolean playReported;

    public static void start(Context context, @Nullable PopinnVideo video) {
        if (video == null) return;

        Intent intent = new Intent(context, MusicVideoPlayerActivity.class);
        intent.putExtra(Constants.MUSIC_VIDEO_OBJECT, video);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bind = ActivityMusicVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(bind.getRoot());

        video = getIntent().getParcelableExtra(Constants.MUSIC_VIDEO_OBJECT);
        if (video == null) {
            finish();
            return;
        }

        setTitle(video.getTitle());
        goFullscreen();
        initSeekGestures();
        // Lets the user turn side-loaded and embedded subtitle tracks on and off.
        bind.musicVideoPlayerView.setShowSubtitleButton(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initSeekGestures() {
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
                toggleControllerVisibility();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                boolean forward = event.getX() >= bind.musicVideoPlayerView.getWidth() / 2f;
                seekBy(forward);
                return true;
            }
        });

        // Every touch is consumed here so PlayerView never sees the taps and so
        // never toggles its controls on a double tap. That makes showing the
        // controls this activity's job, and it waits for onSingleTapConfirmed —
        // a tap is only known not to be the start of a double tap once the
        // double-tap window has passed. Controller buttons are unaffected: as
        // child views they get the touch first, and this listener only runs for
        // events they leave alone.
        bind.musicVideoPlayerView.setOnTouchListener((view, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void toggleControllerVisibility() {
        if (bind == null) return;

        if (bind.musicVideoPlayerView.isControllerFullyVisible()) {
            bind.musicVideoPlayerView.hideController();
        } else {
            bind.musicVideoPlayerView.showController();
        }
    }

    private void seekBy(boolean forward) {
        if (player == null) return;

        // Tapping the same side again keeps adding, the way a video app is
        // expected to behave; switching sides starts over.
        if (accumulatedSeekSeconds == 0 || forward != lastSeekWasForward) {
            accumulatedSeekSeconds = SEEK_STEP_SECONDS;
        } else {
            accumulatedSeekSeconds += SEEK_STEP_SECONDS;
        }
        lastSeekWasForward = forward;

        long deltaMs = (long) SEEK_STEP_SECONDS * 1000 * (forward ? 1 : -1);
        long target = player.getCurrentPosition() + deltaMs;

        long duration = player.getDuration();
        if (duration != C.TIME_UNSET) target = Math.min(target, duration);
        player.seekTo(Math.max(target, 0));

        showSeekFeedback(forward);
    }

    private void showSeekFeedback(boolean forward) {
        TextView shown = forward ? bind.musicVideoSeekForwardLabel : bind.musicVideoSeekBackLabel;
        TextView hidden = forward ? bind.musicVideoSeekBackLabel : bind.musicVideoSeekForwardLabel;

        shown.setText(getString(
                forward ? R.string.music_video_seek_forward : R.string.music_video_seek_back,
                accumulatedSeekSeconds
        ));

        hidden.animate().cancel();
        hidden.setAlpha(0f);
        shown.animate().cancel();
        shown.setAlpha(1f);

        seekFeedbackHandler.removeCallbacks(hideSeekFeedback);
        seekFeedbackHandler.postDelayed(hideSeekFeedback, SEEK_FEEDBACK_TIMEOUT_MS);
    }

    private void hideSeekFeedback() {
        accumulatedSeekSeconds = 0;
        if (bind == null) return;

        bind.musicVideoSeekForwardLabel.animate().alpha(0f).setDuration(200).start();
        bind.musicVideoSeekBackLabel.animate().alpha(0f).setDuration(200).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        preparePlayer();
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        seekFeedbackHandler.removeCallbacks(hideSeekFeedback);
        bind = null;
    }

    private void goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), bind.getRoot());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void preparePlayer() {
        String url = PopinnClient.toAbsoluteUrl(video.getPlaybackUrl() != null ? video.getPlaybackUrl() : video.getVideoUrl());
        if (url == null) {
            showErrorAndFinish();
            return;
        }

        // The token normally exists by now — the list that led here fetched over
        // the same client — but a process restart straight into this screen would
        // otherwise 401, and logging in blocks.
        if (PopinnClient.hasToken()) {
            fetchSubtitlesThenPlay(url);
            return;
        }

        bind.musicVideoProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            PopinnClient.login();
            runOnUiThread(() -> {
                if (bind == null || isFinishing()) return;
                fetchSubtitlesThenPlay(url);
            });
        }).start();
    }

    /**
     * Subtitles are not part of the video payload, so they are fetched before
     * playback starts — side-loaded tracks have to be declared on the MediaItem
     * up front. A failure here is not fatal: the video still plays without them.
     */
    private void fetchSubtitlesThenPlay(String url) {
        PopinnApi api = PopinnClient.getApi();
        if (api == null || video.getId() == null) {
            startPlayback(url, Collections.emptyList());
            return;
        }

        bind.musicVideoProgressBar.setVisibility(View.VISIBLE);
        api.getSubtitles(video.getId()).enqueue(new Callback<List<PopinnSubtitle>>() {
            @Override
            public void onResponse(@NonNull Call<List<PopinnSubtitle>> call, @NonNull Response<List<PopinnSubtitle>> response) {
                if (bind == null || isFinishing()) return;

                List<PopinnSubtitle> subtitles = response.isSuccessful() && response.body() != null
                        ? response.body()
                        : Collections.emptyList();
                startPlayback(url, subtitles);
            }

            @Override
            public void onFailure(@NonNull Call<List<PopinnSubtitle>> call, @NonNull Throwable throwable) {
                if (bind == null || isFinishing()) return;
                startPlayback(url, Collections.emptyList());
            }
        });
    }

    private void startPlayback(String url, List<PopinnSubtitle> subtitles) {
        // Bearer covers the HLS segment requests too, which are relative to the
        // playlist and so carry no signed stream token of their own.
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(PopinnClient.getAuthHeaders());

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                // Takes audio focus, so any music playing in the app pauses.
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (bind != null) {
                    bind.musicVideoProgressBar.setVisibility(playbackState == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                }

                if (playbackState == Player.STATE_READY) rememberDuration();

                // Watching to the end is a finished view whether or not the
                // screen is closed afterwards, so it is reported straight away.
                if (playbackState == Player.STATE_ENDED) {
                    stopWatchClock();
                    reportPlay();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) startWatchClock();
                else stopWatchClock();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                showErrorAndFinish();
            }
        });

        bind.musicVideoPlayerView.setPlayer(player);

        List<MediaItem.SubtitleConfiguration> subtitleConfigurations = toSubtitleConfigurations(subtitles);
        player.setMediaItem(new MediaItem.Builder()
                .setUri(url)
                .setSubtitleConfigurations(subtitleConfigurations)
                .build());

        // The DEFAULT selection flag alone does not always win against the track
        // selector's own language preferences, so name the language too.
        if (!subtitleConfigurations.isEmpty()) {
            String language = subtitleConfigurations.get(0).language;
            if (language != null) {
                player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters().buildUpon()
                                .setPreferredTextLanguage(language)
                                .build()
                );
            }
        }

        if (resumePosition != C.TIME_UNSET) player.seekTo(resumePosition);
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private List<MediaItem.SubtitleConfiguration> toSubtitleConfigurations(List<PopinnSubtitle> subtitles) {
        List<MediaItem.SubtitleConfiguration> configurations = new ArrayList<>();

        for (PopinnSubtitle subtitle : subtitles) {
            String subtitleUrl = PopinnClient.toAbsoluteUrl(subtitle.getUrl());
            if (subtitleUrl == null) continue;

            configurations.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                    // Always WebVTT: the server converts SubRip on the way out.
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(subtitle.getLanguage())
                    .setSelectionFlags(configurations.isEmpty() ? C.SELECTION_FLAG_DEFAULT : 0)
                    .build());
        }

        return configurations;
    }

    private void releasePlayer() {
        if (player == null) return;

        stopWatchClock();
        rememberDuration();
        resumePosition = player.getCurrentPosition();
        if (bind != null) bind.musicVideoPlayerView.setPlayer(null);
        player.release();
        player = null;

        // Only on the way out. Backgrounding the app also lands here, and that
        // is a pause in one viewing, not the end of it — the clock resumes when
        // the activity comes back.
        if (isFinishing()) reportPlay();
    }

    private void startWatchClock() {
        if (watchStartedAtMs == C.TIME_UNSET) watchStartedAtMs = SystemClock.elapsedRealtime();
    }

    private void stopWatchClock() {
        if (watchStartedAtMs == C.TIME_UNSET) return;

        watchedMs += SystemClock.elapsedRealtime() - watchStartedAtMs;
        watchStartedAtMs = C.TIME_UNSET;
    }

    /** Kept for the report, which is sent after the player is gone. */
    private void rememberDuration() {
        if (player == null) return;

        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && duration > 0) {
            knownDurationSeconds = (int) (duration / 1000);
        }
    }

    /**
     * Tells the server how long this video was watched. It decides for itself
     * whether that clears VIEW_THRESHOLD_RATIO and counts as a view, so nothing
     * here needs to know the threshold. Fire and forget: a failed report is not
     * worth interrupting the user over.
     */
    private void reportPlay() {
        if (playReported || video.getId() == null) return;

        long watchedSeconds = watchedMs / 1000;
        if (watchedSeconds < 1) return;

        PopinnApi api = PopinnClient.getApi();
        if (api == null) return;

        playReported = true;

        Integer duration = video.getDuration() != null && video.getDuration() > 0
                ? video.getDuration()
                : (knownDurationSeconds > 0 ? knownDurationSeconds : null);

        api.recordPlay(video.getId(), new PopinnPlayRequest(watchedSeconds, duration))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Play report rejected: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                        Log.w(TAG, "Play report failed", throwable);
                    }
                });
    }

    private void showErrorAndFinish() {
        Toast.makeText(this, R.string.music_video_player_error, Toast.LENGTH_LONG).show();
        finish();
    }
}
