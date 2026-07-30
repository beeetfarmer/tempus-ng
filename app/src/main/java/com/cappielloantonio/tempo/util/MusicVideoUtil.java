package com.cappielloantonio.tempo.util;

import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.popinn.PopinnGlide;
import com.cappielloantonio.tempo.popinn.PopinnVideo;
import com.google.android.material.elevation.SurfaceColors;

import java.util.Locale;

/** Shared formatting and image loading for Popinn music videos. */
public final class MusicVideoUtil {

    private MusicVideoUtil() {
    }

    /**
     * Loads a video thumbnail. Popinn serves these from its own host behind
     * authentication, so this goes through {@link PopinnGlide} rather than the
     * Subsonic-oriented {@link CustomGlideRequest}.
     */
    public static void loadThumbnail(ImageView imageView, PopinnVideo video) {
        Glide.with(imageView.getContext())
                .load(PopinnGlide.toGlideUrl(video.getThumbnailUrl()))
                .placeholder(new ColorDrawable(SurfaceColors.SURFACE_5.getColor(imageView.getContext())))
                .fallback(R.drawable.ic_placeholder_album)
                .error(R.drawable.ic_placeholder_album)
                .transform(new CenterCrop(), new RoundedCorners(CustomGlideRequest.CORNER_RADIUS))
                .into(imageView);
    }

    /** "3:47", or null when the server does not know the duration. */
    @Nullable
    public static String formatDuration(PopinnVideo video) {
        Integer duration = video.getDuration();
        if (duration == null || duration <= 0) {
            String display = video.getDurationDisplay();
            return display == null || display.isEmpty() || "0:00".equals(display) ? null : display;
        }

        long hours = duration / 3600;
        long minutes = (duration % 3600) / 60;
        long seconds = duration % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /**
     * Fills in the secondary line: album and year where known, prefixed by the
     * artist on screens that mix artists together.
     */
    public static void bindSubtitle(TextView label, PopinnVideo video, boolean showArtist) {
        StringBuilder subtitle = new StringBuilder();

        if (showArtist && video.getArtistName() != null && !video.getArtistName().isEmpty()) {
            subtitle.append(video.getArtistName());
        }

        String album = video.getAlbum();
        if (album != null && !album.isEmpty()) {
            appendSeparator(subtitle);
            subtitle.append(album);
        }

        Integer year = video.getYear();
        if (year != null && year > 0) {
            appendSeparator(subtitle);
            subtitle.append(year);
        }

        label.setText(subtitle.toString());
        label.setVisibility(subtitle.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private static void appendSeparator(StringBuilder builder) {
        if (builder.length() > 0) builder.append(" • ");
    }
}
