package com.cappielloantonio.tempo.popinn

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
data class PopinnLoginRequest(
    val email: String,
    val password: String
)

@Keep
data class PopinnTokenPair(
    @SerializedName("access_token")
    var accessToken: String? = null,
    @SerializedName("refresh_token")
    var refreshToken: String? = null,
    @SerializedName("token_type")
    var tokenType: String? = null
)

@Keep
data class PopinnArtist(
    var id: String? = null,
    var name: String? = null,
    @SerializedName("lastfm_artist_name")
    var lastFmArtistName: String? = null,
    @SerializedName("video_count")
    var videoCount: Int = 0,
    @SerializedName("image_url")
    var imageUrl: String? = null
)

@Keep
@Parcelize
data class PopinnVideo(
    var id: String? = null,
    var title: String? = null,
    @SerializedName("artist_id")
    var artistId: String? = null,
    @SerializedName("artist_name")
    var artistName: String? = null,
    var album: String? = null,
    var duration: Int? = null,
    @SerializedName("thumbnail_url")
    var thumbnailUrl: String? = null,
    @SerializedName("preview_url")
    var previewUrl: String? = null,
    @SerializedName("video_url")
    var videoUrl: String? = null,
    // Points at the HLS playlist when the server has a rendition ready, and at
    // the source file otherwise, so it is the only URL playback needs to know.
    @SerializedName("playback_url")
    var playbackUrl: String? = null,
    var year: Int? = null,
    var genre: String? = null,
    @SerializedName("added_at")
    var addedAt: String? = null,
    @SerializedName("duration_display")
    var durationDisplay: String? = null
) : Parcelable

/**
 * Report of how much of a video was actually watched. Whether that amounts to a
 * view is the server's decision, taken from VIEW_THRESHOLD_RATIO.
 */
@Keep
data class PopinnPlayRequest(
    @SerializedName("watched_seconds")
    var watchedSeconds: Double = 0.0,
    @SerializedName("video_duration_seconds")
    var videoDurationSeconds: Int? = null
)

@Keep
data class PopinnSubtitle(
    var id: String? = null,
    @SerializedName("video_id")
    var videoId: String? = null,
    var language: String? = null,
    /** Source format on disk. The url below always serves WebVTT regardless. */
    var format: String? = null,
    var url: String? = null
)

@Keep
data class PopinnVideoPage(
    var items: List<PopinnVideo> = emptyList(),
    var total: Int = 0,
    var skip: Int = 0,
    var limit: Int = 0
)
