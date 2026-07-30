package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.popinn.PopinnClient;
import com.cappielloantonio.tempo.popinn.PopinnRepository;
import com.cappielloantonio.tempo.popinn.PopinnVideoResult;
import com.cappielloantonio.tempo.util.Preferences;

public class MusicVideoCatalogueViewModel extends AndroidViewModel {
    /**
     * Rows fetched per request. Comfortably under the server's ceiling of 200,
     * and small enough that a page arrives quickly over a remote link.
     */
    public static final int PAGE_SIZE = 60;

    private final PopinnRepository popinnRepository;

    /** Total the server reported; -1 until the first page lands. */
    public int total = -1;

    public MusicVideoCatalogueViewModel(@NonNull Application application) {
        super(application);
        popinnRepository = new PopinnRepository();
    }

    public LiveData<PopinnVideoResult> loadPage(int skip) {
        return popinnRepository.getVideoCataloguePage(skip, PAGE_SIZE, sortColumn(), sortOrder());
    }

    public boolean hasMore(int loadedCount) {
        return total < 0 || loadedCount < total;
    }

    public String getSort() {
        return Preferences.getMusicVideoCatalogueSort();
    }

    /** Returns true when the sort actually changed and the list must restart. */
    public boolean setSort(String sort) {
        if (sort.equals(getSort())) return false;

        Preferences.setMusicVideoCatalogueSort(sort);
        total = -1;
        return true;
    }

    private String sortColumn() {
        return Preferences.MUSIC_VIDEO_SORT_DATE_ADDED.equals(getSort())
                ? PopinnClient.SORT_LATEST
                : PopinnClient.SORT_TITLE;
    }

    private String sortOrder() {
        // Newest first for dates; A to Z for titles.
        return Preferences.MUSIC_VIDEO_SORT_DATE_ADDED.equals(getSort())
                ? PopinnClient.SORT_ORDER_DESC
                : PopinnClient.SORT_ORDER_ASC;
    }
}
