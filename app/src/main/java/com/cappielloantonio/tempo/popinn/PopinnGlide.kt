package com.cappielloantonio.tempo.popinn

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaderFactory
import com.bumptech.glide.load.model.LazyHeaders

/**
 * Wraps Popinn asset URLs for Glide, which fetches over its own HTTP stack and
 * so needs the bearer token attached explicitly.
 */
object PopinnGlide {

    /** Null when there is no thumbnail, or no server to fetch it from. */
    @JvmStatic
    fun toGlideUrl(pathOrUrl: String?): GlideUrl? {
        val absolute = PopinnClient.toAbsoluteUrl(pathOrUrl) ?: return null

        // Resolved when the request actually runs: on a cold start the artist
        // page renders before the login that produces the token.
        val headers = LazyHeaders.Builder()
            .addHeader("Authorization", LazyHeaderFactory {
                PopinnClient.getAuthHeaders()["Authorization"]
            })
            .build()

        return GlideUrl(absolute, headers)
    }
}
