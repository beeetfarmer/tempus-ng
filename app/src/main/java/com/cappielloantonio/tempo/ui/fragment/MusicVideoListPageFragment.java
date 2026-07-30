package com.cappielloantonio.tempo.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentMusicVideoListPageBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.activity.MusicVideoPlayerActivity;
import com.cappielloantonio.tempo.ui.adapter.MusicVideoHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.MusicVideoListPageViewModel;

/** Every music video a Popinn artist has, newest first, paged as you scroll. */
@OptIn(markerClass = UnstableApi.class)
public class MusicVideoListPageFragment extends Fragment implements ClickCallback {
    /** Start the next page this many rows before the end. */
    private static final int PREFETCH_DISTANCE = 10;

    private FragmentMusicVideoListPageBinding bind;

    private MainActivity activity;
    private MusicVideoListPageViewModel musicVideoListPageViewModel;
    private MusicVideoHorizontalAdapter musicVideoAdapter;

    private boolean isLoading;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentMusicVideoListPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        musicVideoListPageViewModel = new ViewModelProvider(requireActivity()).get(MusicVideoListPageViewModel.class);

        init();
        initAppBar();
        initMusicVideoListView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        String artistId = requireArguments().getString(Constants.MUSIC_VIDEO_ARTIST_ID);
        String artistName = requireArguments().getString(Constants.MUSIC_VIDEO_ARTIST_NAME);

        // A different artist than last time means the paging state must not carry over.
        if (artistId != null && !artistId.equals(musicVideoListPageViewModel.artistId)) {
            musicVideoListPageViewModel.total = -1;
        }

        musicVideoListPageViewModel.artistId = artistId;
        musicVideoListPageViewModel.artistName = artistName;

        bind.pageTitleLabel.setText(artistName != null ? artistName : getString(R.string.music_video_list_page_title));
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if ((bind.musicVideoInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.music_video_list_page_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void initMusicVideoListView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        bind.musicVideoListRecyclerView.setLayoutManager(layoutManager);

        musicVideoAdapter = new MusicVideoHorizontalAdapter(this);
        bind.musicVideoListRecyclerView.setAdapter(musicVideoAdapter);

        bind.musicVideoListRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || isLoading) return;

                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= musicVideoAdapter.getLoadedCount() - PREFETCH_DISTANCE) {
                    loadNextPage();
                }
            }
        });

        loadNextPage();
    }

    private void loadNextPage() {
        int loaded = musicVideoAdapter.getLoadedCount();
        if (isLoading || !musicVideoListPageViewModel.hasMore(loaded)) return;

        isLoading = true;
        if (bind != null && loaded == 0) bind.musicVideoListProgressBar.setVisibility(View.VISIBLE);

        musicVideoListPageViewModel.loadPage(loaded).observe(getViewLifecycleOwner(), result -> {
            isLoading = false;
            if (bind == null) return;

            bind.musicVideoListProgressBar.setVisibility(View.GONE);
            if (result == null) return;

            musicVideoListPageViewModel.total = result.getTotal();
            musicVideoAdapter.addItems(result.getVideos());
            bind.pageSubtitleLabel.setText(getString(R.string.generic_list_page_count, result.getTotal()));

            // An empty page while more was expected would otherwise loop forever.
            if (result.getVideos().isEmpty()) {
                musicVideoListPageViewModel.total = musicVideoAdapter.getLoadedCount();
            }
        });
    }

    @Override
    public void onMusicVideoClick(Bundle bundle) {
        MusicVideoPlayerActivity.start(requireContext(), bundle.getParcelable(Constants.MUSIC_VIDEO_OBJECT));
    }
}
