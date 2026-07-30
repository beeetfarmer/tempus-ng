package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.popinn.PopinnRepository;
import com.cappielloantonio.tempo.popinn.PopinnVideo;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.DirectoryRepository;
import com.cappielloantonio.tempo.repository.GenreRepository;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Genre;
import com.cappielloantonio.tempo.subsonic.models.Indexes;
import com.cappielloantonio.tempo.subsonic.models.MusicFolder;
import com.cappielloantonio.tempo.subsonic.models.Playlist;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {
    private static final String TAG = "LibraryViewModel";

    /** Videos in the library carousel, before "See all" takes over. */
    private static final int MUSIC_VIDEO_SAMPLE_SIZE = 10;

    private final DirectoryRepository directoryRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final PlaylistRepository playlistRepository;
    private final PopinnRepository popinnRepository;

    private final MutableLiveData<List<MusicFolder>> musicFolders = new MutableLiveData<>(null);
    private final MutableLiveData<Indexes> indexes = new MutableLiveData<>(null);
    private final MutableLiveData<List<Playlist>> playlistSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> sampleAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> sampleArtist = new MutableLiveData<>(null);
    private final MutableLiveData<List<Genre>> sampleGenres = new MutableLiveData<>(null);
    private final MutableLiveData<List<PopinnVideo>> sampleMusicVideos = new MutableLiveData<>(null);

    public LibraryViewModel(@NonNull Application application) {
        super(application);

        directoryRepository = new DirectoryRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        genreRepository = new GenreRepository();
        playlistRepository = new PlaylistRepository();
        popinnRepository = new PopinnRepository();
    }

    public LiveData<List<MusicFolder>> getMusicFolders(LifecycleOwner owner) {
        if (musicFolders.getValue() == null) {
            directoryRepository.getMusicFolders().observe(owner, musicFolders::postValue);
        }

        return musicFolders;
    }

    public LiveData<Indexes> getIndexes(LifecycleOwner owner) {
        if (indexes.getValue() == null) {
            directoryRepository.getIndexes("0", null).observe(owner, indexes::postValue);
        }

        return indexes;
    }

    public LiveData<List<AlbumID3>> getAlbumSample(LifecycleOwner owner) {
        if (sampleAlbum.getValue() == null) {
            albumRepository.getAlbums("random", 10, null, null).observe(owner, sampleAlbum::postValue);
        }

        return sampleAlbum;
    }

    public LiveData<List<ArtistID3>> getArtistSample(LifecycleOwner owner) {
        if (sampleArtist.getValue() == null) {
            artistRepository.getArtists(true, 10).observe(owner, sampleArtist::postValue);
        }

        return sampleArtist;
    }

    public LiveData<List<Genre>> getGenreSample(LifecycleOwner owner) {
        if (sampleGenres.getValue() == null) {
            genreRepository.getGenres(true, 15).observe(owner, sampleGenres::postValue);
        }

        return sampleGenres;
    }

    public LiveData<List<Playlist>> getPlaylistSample(LifecycleOwner owner) {
        if (playlistSample.getValue() == null) {
            playlistRepository.getPlaylists(true, 10).observe(owner, playlistSample::postValue);
        }

        return playlistSample;
    }

    /**
     * A random handful of music videos from the Popinn server, or an empty list
     * when none is configured or it cannot be reached — the section hides itself
     * in that case.
     */
    public LiveData<List<PopinnVideo>> getMusicVideoSample(LifecycleOwner owner) {
        if (sampleMusicVideos.getValue() == null) {
            popinnRepository.getRandomVideos(MUSIC_VIDEO_SAMPLE_SIZE)
                    .observe(owner, result -> sampleMusicVideos.postValue(result.getVideos()));
        }

        return sampleMusicVideos;
    }

    public void refreshMusicVideoSample(LifecycleOwner owner) {
        popinnRepository.getRandomVideos(MUSIC_VIDEO_SAMPLE_SIZE)
                .observe(owner, result -> sampleMusicVideos.postValue(result.getVideos()));
    }

    public void refreshAlbumSample(LifecycleOwner owner) {
        albumRepository.getAlbums("random", 10, null, null).observe(owner, sampleAlbum::postValue);
    }

    public void refreshArtistSample(LifecycleOwner owner) {
        artistRepository.getArtists(true, 10).observe(owner, sampleArtist::postValue);
    }

    public void refreshGenreSample(LifecycleOwner owner) {
        genreRepository.getGenres(true, 15).observe(owner, sampleGenres::postValue);
    }

    public void refreshPlaylistSample(LifecycleOwner owner) {
        playlistRepository.getPlaylists(true, 10).observe(owner, playlistSample::postValue);
    }
}
