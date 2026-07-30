package com.cappielloantonio.tempo.popinn

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PopinnApi {
    @POST("api/v1/auth/login")
    fun login(@Body body: PopinnLoginRequest): Call<PopinnTokenPair>

    @GET("api/v1/artists/")
    fun searchArtists(
        @Query("search") search: String,
        @Query("limit") limit: Int
    ): Call<List<PopinnArtist>>

    /**
     * Null filters drop their query parameter, so the same call serves one
     * artist, a title search, or the whole library. `search` matches the title
     * only — the server has no artist-aware video search.
     */
    @GET("api/v1/videos/")
    fun getVideos(
        @Query("artist_id") artistId: String?,
        @Query("search") search: String?,
        @Query("sort_by") sortBy: String,
        @Query("sort_order") sortOrder: String,
        @Query("skip") skip: Int,
        @Query("limit") limit: Int
    ): Call<PopinnVideoPage>

    // Subtitles are not part of the video payload, so they are a separate trip.
    @GET("api/v1/videos/{video_id}/subtitles")
    fun getSubtitles(@Path("video_id") videoId: String): Call<List<PopinnSubtitle>>

    // No CSRF header needed: the server only enforces it when an access cookie
    // is present, and this client authenticates by bearer token.
    @POST("api/v1/videos/{video_id}/plays")
    fun recordPlay(
        @Path("video_id") videoId: String,
        @Body body: PopinnPlayRequest
    ): Call<Void>
}
