package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.model.RecentSearch;
import com.cappielloantonio.tempo.popinn.PopinnRepository;
import com.cappielloantonio.tempo.popinn.PopinnVideoResult;
import com.cappielloantonio.tempo.repository.SearchingRepository;
import com.cappielloantonio.tempo.subsonic.models.SearchResult2;
import com.cappielloantonio.tempo.subsonic.models.SearchResult3;

import java.util.ArrayList;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private static final String TAG = "SearchViewModel";

    /** Videos shown per search, matching the depth of the other result lists. */
    private static final int MUSIC_VIDEO_RESULT_LIMIT = 20;

    private String query = "";

    private final SearchingRepository searchingRepository;
    private final PopinnRepository popinnRepository;

    public SearchViewModel(@NonNull Application application) {
        super(application);

        searchingRepository = new SearchingRepository();
        popinnRepository = new PopinnRepository();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;

        if (!query.isEmpty()) {
            insertNewSearch(query);
        }
    }

    public LiveData<SearchResult2> search2(String title) {
        return searchingRepository.search2(title);
    }

    public LiveData<SearchResult3> search3(String title) {
        return searchingRepository.search3(title);
    }

    /**
     * Music videos matching the query, from the Popinn server. Empty when none
     * is configured or it cannot be reached, which hides the section.
     */
    public LiveData<PopinnVideoResult> searchMusicVideos(String query) {
        return popinnRepository.searchVideos(query, MUSIC_VIDEO_RESULT_LIMIT);
    }

    public void insertNewSearch(String search) {
        searchingRepository.insert(new RecentSearch(search, System.currentTimeMillis() / 1000L));
    }

    public void deleteRecentSearch(String search) {
        searchingRepository.delete(new RecentSearch(search, 0));
    }

    public LiveData<List<String>> getSearchSuggestion(String query) {
        return searchingRepository.getSuggestions(query);
    }

    public List<String> getRecentSearchSuggestion() {
        ArrayList<String> suggestions = new ArrayList<>();
        suggestions.addAll(searchingRepository.getRecentSearchSuggestion());

        return suggestions;
    }
}
