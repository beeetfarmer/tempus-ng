package com.cappielloantonio.tempo.popinn

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges Subsonic artists to Popinn ones.
 *
 * The two libraries share no identifiers, so an artist is matched by name and
 * the resulting id cached — including the misses, which are the common case for
 * a music library much larger than the video one.
 */
class PopinnRepository {

    companion object {
        private const val TAG = "PopinnRepository"
        private const val ARTIST_SEARCH_LIMIT = 50

        /** Sentinel for "searched, and this artist has no Popinn entry". */
        private const val NO_MATCH = ""

        private val artistIdCache = ConcurrentHashMap<String, String>()

        @JvmStatic
        fun clearCache() = artistIdCache.clear()

        private fun normalize(name: String): String {
            return name.lowercase().filter { it.isLetterOrDigit() }
        }
    }

    /**
     * Latest videos for a Subsonic artist name, newest first. Emits an empty
     * result rather than an error when the server is unreachable or the artist
     * simply has no videos — the section is meant to disappear quietly.
     */
    fun getArtistVideos(artistName: String?, limit: Int): LiveData<PopinnVideoResult> {
        val result = MutableLiveData<PopinnVideoResult>()

        if (artistName.isNullOrBlank() || !PopinnClient.isConfigured()) {
            result.value = PopinnVideoResult.empty()
            return result
        }

        val api = PopinnClient.getApi()
        if (api == null) {
            result.value = PopinnVideoResult.empty()
            return result
        }

        val cachedId = artistIdCache[normalize(artistName)]
        when {
            cachedId == NO_MATCH -> result.value = PopinnVideoResult.empty()
            cachedId != null -> fetchVideos(api, cachedId, 0, limit, result)
            else -> resolveThenFetch(api, artistName, limit, result)
        }

        return result
    }

    /** One page of videos for an already resolved Popinn artist id. */
    fun getArtistVideoPage(artistId: String, skip: Int, limit: Int): LiveData<PopinnVideoResult> {
        val result = MutableLiveData<PopinnVideoResult>()

        val api = PopinnClient.getApi()
        if (api == null) {
            result.value = PopinnVideoResult.empty()
            return result
        }

        fetchVideos(api, artistId, skip, limit, result)
        return result
    }

    /**
     * One page of the whole video library, for the catalogue screen.
     *
     * Paged rather than fetched whole: the server caps a request at 200 items,
     * and a library of any size would not fit in memory anyway.
     */
    fun getVideoCataloguePage(
        skip: Int,
        limit: Int,
        sortBy: String,
        sortOrder: String
    ): LiveData<PopinnVideoResult> {
        val result = MutableLiveData<PopinnVideoResult>()

        val api = PopinnClient.getApi()
        if (api == null) {
            result.value = PopinnVideoResult.empty()
            return result
        }

        fetchVideos(api, null, skip, limit, result, sortBy, sortOrder)
        return result
    }

    /**
     * A random handful from the library, for the carousel.
     *
     * There is no random sort server-side, so a random window is taken instead:
     * one request establishes the total, and a second reads `count` items from a
     * random offset. The window is then shuffled so the order does not betray
     * the underlying sort. Small libraries skip the second request entirely.
     */
    fun getRandomVideos(count: Int): LiveData<PopinnVideoResult> {
        val result = MutableLiveData<PopinnVideoResult>()

        if (!PopinnClient.isConfigured()) {
            result.value = PopinnVideoResult.empty()
            return result
        }

        val api = PopinnClient.getApi()
        if (api == null) {
            result.value = PopinnVideoResult.empty()
            return result
        }

        val limit = count.coerceIn(1, PopinnClient.MAX_PAGE_SIZE)
        api.getVideos(null, PopinnClient.SORT_LATEST, PopinnClient.SORT_ORDER_DESC, 0, limit)
            .enqueue(object : Callback<PopinnVideoPage> {
                override fun onResponse(call: Call<PopinnVideoPage>, response: Response<PopinnVideoPage>) {
                    val page = response.body()
                    if (!response.isSuccessful || page == null) {
                        Log.w(TAG, "Random video fetch failed: HTTP ${response.code()}")
                        result.postValue(PopinnVideoResult.empty())
                        return
                    }

                    if (page.total <= limit) {
                        result.postValue(PopinnVideoResult(null, page.items.shuffled(), page.total, 0))
                        return
                    }

                    val randomSkip = (0..(page.total - limit)).random()
                    api.getVideos(null, PopinnClient.SORT_LATEST, PopinnClient.SORT_ORDER_DESC, randomSkip, limit)
                        .enqueue(object : Callback<PopinnVideoPage> {
                            override fun onResponse(call: Call<PopinnVideoPage>, response: Response<PopinnVideoPage>) {
                                val randomPage = response.body()
                                if (!response.isSuccessful || randomPage == null) {
                                    // Fall back to the page already in hand.
                                    result.postValue(PopinnVideoResult(null, page.items.shuffled(), page.total, 0))
                                    return
                                }

                                result.postValue(
                                    PopinnVideoResult(null, randomPage.items.shuffled(), randomPage.total, randomSkip)
                                )
                            }

                            override fun onFailure(call: Call<PopinnVideoPage>, throwable: Throwable) {
                                Log.w(TAG, "Random video fetch failed", throwable)
                                result.postValue(PopinnVideoResult(null, page.items.shuffled(), page.total, 0))
                            }
                        })
                }

                override fun onFailure(call: Call<PopinnVideoPage>, throwable: Throwable) {
                    Log.w(TAG, "Random video fetch failed", throwable)
                    result.postValue(PopinnVideoResult.empty())
                }
            })

        return result
    }

    private fun resolveThenFetch(
        api: PopinnApi,
        artistName: String,
        limit: Int,
        result: MutableLiveData<PopinnVideoResult>
    ) {
        api.searchArtists(artistName, ARTIST_SEARCH_LIMIT)
            .enqueue(object : Callback<List<PopinnArtist>> {
                override fun onResponse(
                    call: Call<List<PopinnArtist>>,
                    response: Response<List<PopinnArtist>>
                ) {
                    val match = if (response.isSuccessful) {
                        bestMatch(response.body(), artistName)
                    } else {
                        Log.w(TAG, "Artist search failed: HTTP ${response.code()}")
                        null
                    }

                    val matchedId = match?.id
                    if (matchedId == null) {
                        artistIdCache[normalize(artistName)] = NO_MATCH
                        result.postValue(PopinnVideoResult.empty())
                        return
                    }

                    artistIdCache[normalize(artistName)] = matchedId
                    fetchVideos(api, matchedId, 0, limit, result)
                }

                override fun onFailure(call: Call<List<PopinnArtist>>, throwable: Throwable) {
                    // Not cached as a miss: the server may just be off-net right
                    // now, and the next visit should try again.
                    Log.w(TAG, "Artist search failed", throwable)
                    result.postValue(PopinnVideoResult.empty())
                }
            })
    }

    private fun fetchVideos(
        api: PopinnApi,
        artistId: String?,
        skip: Int,
        limit: Int,
        result: MutableLiveData<PopinnVideoResult>,
        sortBy: String = PopinnClient.SORT_LATEST,
        sortOrder: String = PopinnClient.SORT_ORDER_DESC
    ) {
        api.getVideos(
            artistId,
            sortBy,
            sortOrder,
            skip,
            limit.coerceIn(1, PopinnClient.MAX_PAGE_SIZE)
        ).enqueue(object : Callback<PopinnVideoPage> {
            override fun onResponse(call: Call<PopinnVideoPage>, response: Response<PopinnVideoPage>) {
                val page = response.body()
                if (!response.isSuccessful || page == null) {
                    Log.w(TAG, "Video fetch failed: HTTP ${response.code()}")
                    result.postValue(PopinnVideoResult.empty())
                    return
                }

                result.postValue(PopinnVideoResult(artistId, page.items, page.total, page.skip))
            }

            override fun onFailure(call: Call<PopinnVideoPage>, throwable: Throwable) {
                Log.w(TAG, "Video fetch failed", throwable)
                result.postValue(PopinnVideoResult.empty())
            }
        })
    }

    /**
     * Popinn's artist search is a substring match, so "Drake" also returns
     * "Drake Bell". Only an exact name — ignoring case and punctuation, which is
     * what separates "AC/DC" from "ACDC" — is treated as the same artist.
     */
    private fun bestMatch(candidates: List<PopinnArtist>?, artistName: String): PopinnArtist? {
        if (candidates.isNullOrEmpty()) return null

        val target = normalize(artistName)
        return candidates.firstOrNull { candidate ->
            val name = candidate.name?.let { normalize(it) }
            val lastFmName = candidate.lastFmArtistName?.let { normalize(it) }
            candidate.id != null && (name == target || lastFmName == target)
        }
    }
}

/** A page of an artist's videos, plus the id needed to ask for more. */
data class PopinnVideoResult(
    val artistId: String?,
    val videos: List<PopinnVideo>,
    val total: Int,
    val skip: Int
) {
    companion object {
        fun empty() = PopinnVideoResult(null, emptyList(), 0, 0)
    }
}
