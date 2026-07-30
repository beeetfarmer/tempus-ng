package com.cappielloantonio.tempo.popinn

import android.util.Log
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.util.Preferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Talks to a Popinn music video server.
 *
 * Popinn issues session cookies on login but also accepts `Authorization:
 * Bearer` on both the API and the `/media` and `/data` asset routes, so this
 * holds a bearer token rather than a cookie jar — the same header can then be
 * handed to Glide and ExoPlayer, which have no access to OkHttp's cookies.
 */
object PopinnClient {
    private const val TAG = "PopinnClient"

    const val MAX_PAGE_SIZE = 200
    const val SORT_LATEST = "file_created_at"
    const val SORT_ORDER_DESC = "desc"

    private var retrofit: Retrofit? = null
    private var builtForUrl: String? = null

    @Volatile
    private var accessToken: String? = null

    /** Normalised base URL, or null when no server has been configured. */
    @JvmStatic
    fun getBaseUrl(): String? {
        val raw = Preferences.getPopinnServerUrl()?.trim()
        if (raw.isNullOrEmpty()) return null

        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw
        } else {
            "http://$raw"
        }

        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    @JvmStatic
    fun isConfigured(): Boolean {
        return getBaseUrl() != null &&
                !Preferences.getPopinnEmail().isNullOrBlank() &&
                !Preferences.getPopinnPassword().isNullOrBlank()
    }

    @JvmStatic
    @Synchronized
    fun getApi(): PopinnApi? {
        val baseUrl = getBaseUrl() ?: return null

        if (retrofit == null || builtForUrl != baseUrl) {
            retrofit = try {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(buildAuthenticatedClient())
                    .build()
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Unusable Popinn server URL: $baseUrl", exception)
                return null
            }
            builtForUrl = baseUrl
        }

        return retrofit?.create(PopinnApi::class.java)
    }

    /** Drops the cached client and token, so the next call picks up new settings. */
    @JvmStatic
    @Synchronized
    fun reset() {
        retrofit = null
        builtForUrl = null
        accessToken = null
    }

    /**
     * Turns a `/media/...` or `/data/...` path from the API into something a
     * player or image loader can fetch. Absolute URLs are passed through, since
     * artist images may point off-server.
     */
    @JvmStatic
    fun toAbsoluteUrl(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        if (pathOrUrl.startsWith("http://", true) || pathOrUrl.startsWith("https://", true)) {
            return pathOrUrl
        }

        val baseUrl = getBaseUrl() ?: return null
        return baseUrl + pathOrUrl.removePrefix("/")
    }

    /**
     * Bearer header for asset requests made outside Retrofit. Returns an empty
     * map when no token has been obtained yet; URLs carrying a signed `st=`
     * stream token authenticate on their own, so this is best-effort.
     */
    @JvmStatic
    fun getAuthHeaders(): Map<String, String> {
        val token = accessToken ?: return emptyMap()
        return mapOf("Authorization" to "Bearer $token")
    }

    /**
     * Logs in and caches the access token. Blocking — call from a worker thread.
     * Returns the token, or null if the server or credentials are unusable.
     */
    @JvmStatic
    fun login(): String? {
        val baseUrl = getBaseUrl() ?: return null
        val email = Preferences.getPopinnEmail()?.trim()
        val password = Preferences.getPopinnPassword()
        if (email.isNullOrEmpty() || password.isNullOrEmpty()) return null

        // A bare client: the authenticated one would recurse back into here.
        val loginApi = try {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(buildBaseClient().build())
                .build()
                .create(PopinnApi::class.java)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unusable Popinn server URL: $baseUrl", exception)
            return null
        }

        return try {
            val response = loginApi.login(PopinnLoginRequest(email, password)).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Popinn login rejected: HTTP ${response.code()}")
                accessToken = null
                return null
            }
            val token = response.body()?.accessToken
            accessToken = token
            token
        } catch (exception: Exception) {
            Log.w(TAG, "Popinn login failed", exception)
            null
        }
    }

    private fun buildAuthenticatedClient(): OkHttpClient {
        return buildBaseClient()
            .addInterceptor(authInterceptor)
            .build()
    }

    private fun buildBaseClient(): OkHttpClient.Builder {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .callTimeout(1, TimeUnit.MINUTES)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()

        val token = accessToken ?: login()
        var response = chain.proceed(original.withBearer(token))

        // Access tokens expire; one silent re-login beats surfacing an error the
        // user can do nothing about.
        if (response.code == 401) {
            response.close()
            accessToken = null
            val refreshed = login()
            response = chain.proceed(original.withBearer(refreshed))
        }

        response
    }

    private fun Request.withBearer(token: String?): Request {
        if (token.isNullOrEmpty()) return this
        return newBuilder().header("Authorization", "Bearer $token").build()
    }

    /** Verifies the configured server and credentials. Blocking. */
    @JvmStatic
    fun testConnection(): Boolean {
        accessToken = null
        return login() != null
    }

    @JvmStatic
    fun hasToken(): Boolean = accessToken != null
}
