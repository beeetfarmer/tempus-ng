package com.cappielloantonio.tempo.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentMusicVideoCatalogueBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.popinn.PopinnVideoResult;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.activity.MusicVideoPlayerActivity;
import com.cappielloantonio.tempo.ui.adapter.MusicVideoHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.MusicVideoCatalogueViewModel;

/**
 * The whole music video library.
 *
 * Built for a catalogue far larger than fits in memory: rows arrive a page at a
 * time as you scroll, never more than one request is in flight, and the list
 * grows by range insert rather than being rebuilt. Sorting is done by the
 * server, so changing it restarts paging instead of reordering anything held
 * locally — a client-side sort would only ever order the part already fetched.
 */
@OptIn(markerClass = UnstableApi.class)
public class MusicVideoCatalogueFragment extends Fragment implements ClickCallback {
    /** Start the next page this many rows before the end. */
    private static final int PREFETCH_DISTANCE = 15;

    private FragmentMusicVideoCatalogueBinding bind;

    private MainActivity activity;
    private MusicVideoCatalogueViewModel musicVideoCatalogueViewModel;
    private MusicVideoHorizontalAdapter musicVideoAdapter;

    private boolean isLoading;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentMusicVideoCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        musicVideoCatalogueViewModel = new ViewModelProvider(requireActivity()).get(MusicVideoCatalogueViewModel.class);

        initAppBar();
        initMusicVideoCatalogueView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
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
                bind.toolbar.setTitle(R.string.music_video_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void initMusicVideoCatalogueView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        bind.musicVideoCatalogueRecyclerView.setLayoutManager(layoutManager);
        bind.musicVideoCatalogueRecyclerView.setHasFixedSize(true);

        musicVideoAdapter = new MusicVideoHorizontalAdapter(this);
        bind.musicVideoCatalogueRecyclerView.setAdapter(musicVideoAdapter);

        bind.musicVideoCatalogueRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || isLoading) return;

                if (layoutManager.findLastVisibleItemPosition() >= musicVideoAdapter.getLoadedCount() - PREFETCH_DISTANCE) {
                    loadNextPage();
                }
            }
        });

        bind.musicVideoSortImageView.setOnClickListener(view -> showSortMenu(view));

        // A fresh adapter starts empty, so paging always restarts from the top
        // rather than trusting a total left over from a previous visit.
        musicVideoCatalogueViewModel.total = -1;
        loadNextPage();
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.sort_music_video_popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            String sort;
            if (menuItem.getItemId() == R.id.menu_music_video_sort_alphabetical) {
                sort = Preferences.MUSIC_VIDEO_SORT_ALPHABETICAL;
            } else if (menuItem.getItemId() == R.id.menu_music_video_sort_date_added) {
                sort = Preferences.MUSIC_VIDEO_SORT_DATE_ADDED;
            } else {
                return false;
            }

            if (musicVideoCatalogueViewModel.setSort(sort)) restartPaging();
            return true;
        });

        popup.show();
    }

    /** Drops everything loaded and starts again under the new sort. */
    private void restartPaging() {
        if (bind == null) return;

        isLoading = false;
        musicVideoAdapter.clear();
        bind.musicVideoCatalogueRecyclerView.scrollToPosition(0);
        loadNextPage();
    }

    private void loadNextPage() {
        int loaded = musicVideoAdapter.getLoadedCount();
        if (isLoading || !musicVideoCatalogueViewModel.hasMore(loaded)) return;

        isLoading = true;
        if (bind != null && loaded == 0) bind.musicVideoCatalogueProgressBar.setVisibility(View.VISIBLE);

        // Each page is a one-shot LiveData. The observer is removed as soon as it
        // fires, so a long scroll does not leave one behind per page.
        LiveData<PopinnVideoResult> source = musicVideoCatalogueViewModel.loadPage(loaded);
        source.observe(getViewLifecycleOwner(), new Observer<PopinnVideoResult>() {
            @Override
            public void onChanged(@Nullable PopinnVideoResult result) {
                source.removeObserver(this);
                onPageLoaded(result);
            }
        });
    }

    private void onPageLoaded(@Nullable PopinnVideoResult result) {
        isLoading = false;
        if (bind == null) return;

        bind.musicVideoCatalogueProgressBar.setVisibility(View.GONE);
        if (result == null) return;

        musicVideoCatalogueViewModel.total = result.getTotal();
        musicVideoAdapter.addItems(result.getVideos());
        bind.pageSubtitleLabel.setText(getString(R.string.generic_list_page_count, result.getTotal()));

        // An empty page while more was expected would otherwise loop forever.
        if (result.getVideos().isEmpty()) {
            musicVideoCatalogueViewModel.total = musicVideoAdapter.getLoadedCount();
        }
    }

    @Override
    public void onMusicVideoClick(Bundle bundle) {
        MusicVideoPlayerActivity.start(requireContext(), bundle.getParcelable(Constants.MUSIC_VIDEO_OBJECT));
    }
}
