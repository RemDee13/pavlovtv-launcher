package io.github.remdee13.pavlovtv.data

import android.content.Context
import io.github.remdee13.pavlovtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class Http(context: Context) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http"), 12L * 1024 * 1024))
        // Wikimedia and others ask API clients for an identifying User-Agent.
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.url.host.endsWith("artic.edu")) {
                // The Art Institute's CDN rejects unknown user agents; its API docs ask clients to
                // identify themselves with this header instead and keep the library default.
                builder.header("AIC-User-Agent", USER_AGENT)
            } else if (request.url.host.endsWith("bing.com") || request.url.host.endsWith("yahoo.com")) {
                // Bing News (RSS) and Yahoo Finance (charts) answer only browser-style agent strings; this one still names us.
                builder.header("User-Agent", "Mozilla/5.0 (compatible; PavlovTV/${BuildConfig.VERSION_NAME}; +$REPO_URL)")
            } else {
                builder.header("User-Agent", USER_AGENT)
            }
            chain.proceed(builder.build())
        })
        .build()

    suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body?.string() ?: throw IOException("Empty body for $url")
        }
    }

    /** Returns the raw bytes plus the charset from Content-Type, if the server sent one. */
    suspend fun getBytes(url: String): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty body for $url")
            val charset = body.contentType()?.charset()?.name()
            body.bytes() to charset
        }
    }

    /**
     * Reads at most [maxBytes] of the body: enough for a page's <head> (preview image) or a
     * whole article, without letting a huge page fill the memory of a 1 GB TV.
     */
    suspend fun getPrefix(url: String, maxBytes: Long): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("Accept", "text/html,application/xhtml+xml").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty body for $url")
            val source = body.source()
            source.request(maxBytes)
            val size = minOf(source.buffer.size, maxBytes)
            source.buffer.readByteArray(size) to body.contentType()?.charset()?.name()
        }
    }

    suspend fun getJson(url: String): JsonElement = json.parseToJsonElement(getString(url))

    companion object {
        const val REPO_URL = "https://github.com/RemDee13/pavlovtv-launcher"
        val USER_AGENT = "PavlovTV/${BuildConfig.VERSION_NAME} (Android TV launcher; +$REPO_URL)"
        val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.num(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
}

internal fun JsonElement.primitiveOrNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
