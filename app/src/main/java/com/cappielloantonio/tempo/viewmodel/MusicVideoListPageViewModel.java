package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.popinn.PopinnArtistVideos;
import com.cappielloantonio.tempo.popinn.PopinnRepository;

public class MusicVideoListPageViewModel extends AndroidViewModel {
    public static final int PAGE_SIZE = 50;

    private final PopinnRepository popinnRepository;

    public String artistId;
    public String artistName;

    /** Total the server reported for this artist; -1 until the first page lands. */
    public int total = -1;

    public MusicVideoListPageViewModel(@NonNull Application application) {
        super(application);
        popinnRepository = new PopinnRepository();
    }

    public LiveData<PopinnArtistVideos> loadPage(int skip) {
        return popinnRepository.getArtistVideoPage(artistId, skip, PAGE_SIZE);
    }

    public boolean hasMore(int loadedCount) {
        return total < 0 || loadedCount < total;
    }
}
