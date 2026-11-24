package io.github.remdee13.pavlovtv.wallpaper

import android.content.Context
import androidx.core.text.HtmlCompat
import io.github.remdee13.pavlovtv.data.Http
import io.github.remdee13.pavlovtv.data.OnlineSource
import io.github.remdee13.pavlovtv.data.num
import io.github.remdee13.pavlovtv.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class RemoteImage(val url: String, val caption: String = "")

@Serializable
private data class FeedCache(val source: OnlineSource, val day: String, val images: List<RemoteImage>)

/**
 * Free image sources that need no account:
 *  - Bing image of the day (last 8 days)
 *  - Lorem Picsum (photos from Unsplash)
 *  - Wikimedia Commons picture of the day (CC licensed, attribution in caption)
 *  - Art Institute of Chicago public-domain artworks (CC0)
 *  - NASA Image and Video Library (wide space and Earth photos, no key)
 */
class ImageFeeds(context: Context, private val http: Http) {
    private val dir = File(context.cacheDir, "feeds").apply { mkdirs() }

    suspend fun images(source: OnlineSource, language: String, force: Boolean = false): List<RemoteImage> {
        val today = LocalDate.now().toString()
        val file = File(dir, "${source.name.lowercase()}.json")
        val cached = runCatching { Http.json.decodeFromString<FeedCache>(file.readText()) }.getOrNull()
        if (!force && cached != null && cached.day == today && cached.images.isNotEmpty()) return cached.images
        val fresh = runCatching { fetch(source, language) }.getOrDefault(emptyList())
        if (fresh.isEmpty()) return cached?.images.orEmpty()
        withContext(Dispatchers.IO) { runCatching { file.writeText(Http.json.encodeToString(FeedCache(source, today, fresh))) } }
        return fresh
    }

    private suspend fun fetch(source: OnlineSource, language: String): List<RemoteImage> = when (source) {
        OnlineSource.BING -> bing(language)
        OnlineSource.PICSUM -> picsum()
        OnlineSource.WIKIMEDIA -> wikimedia(language)
        OnlineSource.ART -> art()
        OnlineSource.NASA -> nasa()
    }

    private suspend fun bing(language: String): List<RemoteImage> {
        val market = mapOf("ru" to "ru-RU", "es" to "es-ES", "tr" to "tr-TR", "de" to "de-DE", "fr" to "fr-FR", "pt" to "pt-BR")[language] ?: "en-US"
        val root = http.getJson("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=$market").jsonObject
        return root["images"]!!.jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            val base = o.str("urlbase") ?: return@mapNotNull null
            RemoteImage("https://www.bing.com${base}_1920x1080.jpg", o.str("copyright").orEmpty())
        }
    }

    private suspend fun picsum(): List<RemoteImage> {
        val page = (1..10).random()
        val list = http.getJson("https://picsum.photos/v2/list?page=$page&limit=100") as JsonArray
        return list.shuffled().take(40).mapNotNull { element ->
            val o = element.jsonObject
            val id = o.str("id") ?: return@mapNotNull null
            val width = o.num("width") ?: 0.0
            val height = o.num("height") ?: 1.0
            if (width < height) return@mapNotNull null // landscape only
            RemoteImage("https://picsum.photos/id/$id/1920/1080", "${o.str("author").orEmpty()} · Unsplash")
        }
    }

    private suspend fun wikimedia(language: String): List<RemoteImage> {
        val local = wikimediaDays(language)
        return if (local.isEmpty() && language != "en") wikimediaDays("en") else local
    }

    private suspend fun wikimediaDays(lang: String): List<RemoteImage> = coroutineScope {
        val format = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val days = (0 until 8).map { LocalDate.now().minusDays(it.toLong()) }
        days.map { day ->
            async {
                runCatching {
                    val root = http.getJson("https://$lang.wikipedia.org/api/rest_v1/feed/featured/${day.format(format)}").jsonObject
                    wikimediaImage(root["image"]?.jsonObject)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    private fun wikimediaImage(image: JsonObject?): RemoteImage? {
        image ?: return null
        val original = image["image"]?.jsonObject ?: return null
        val source = original.str("source")?.substringBefore('?') ?: return null
        val width = original.num("width") ?: 0.0
        val height = original.num("height") ?: 1.0
        val ext = source.substringAfterLast('.').lowercase()
        if (ext !in setOf("jpg", "jpeg", "png") || width < height) return null
        val url = if (width > 1920 && "/wikipedia/commons/" in source) {
            val file = source.substringAfterLast('/')
            source.replace("/wikipedia/commons/", "/wikipedia/commons/thumb/") + "/1920px-" + file
        } else {
            source
        }
        val description = image["description"]?.jsonObject?.str("text")?.let(::plain).orEmpty()
        val artist = image["artist"]?.jsonObject?.str("text")?.let(::plain).orEmpty()
        val license = image["license"]?.jsonObject?.str("type").orEmpty()
        val caption = listOf(description.take(140), listOf(artist, license).filter { it.isNotBlank() }.joinToString(" · "))
            .filter { it.isNotBlank() }
            .joinToString(" — ")
        return RemoteImage(url, caption)
    }

    private suspend fun art(): List<RemoteImage> {
        val topic = listOf("landscape", "sea", "garden", "river", "mountains", "city", "sunset", "harbor", "forest", "winter").random()
        val url = "https://api.artic.edu/api/v1/artworks/search?" +
            "query%5Bterm%5D%5Bis_public_domain%5D=true&q=$topic&limit=80&fields=id,title,artist_title,image_id,thumbnail"
        val data = http.getJson(url).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val o = element.jsonObject
            val imageId = o.str("image_id") ?: return@mapNotNull null
            val thumb = o["thumbnail"] as? JsonObject ?: return@mapNotNull null
            val w = thumb.num("width") ?: return@mapNotNull null
            val h = thumb.num("height") ?: return@mapNotNull null
            if (w < h * 1.15) return@mapNotNull null
            val caption = listOfNotNull(o.str("title"), o.str("artist_title")).joinToString(" — ") + " · Art Institute of Chicago"
            RemoteImage("https://www.artic.edu/iiif/2/$imageId/full/1686,/0/default.jpg", caption)
        }.shuffled().take(40)
    }

    /** NASA Image and Video Library: no key needed, landscape photos at 1920 px when available. */
    private suspend fun nasa(): List<RemoteImage> {
        val topic = listOf("nebula", "galaxy", "earth from space", "aurora", "saturn", "jupiter", "milky way", "hubble", "webb", "mars surface").random()
        val url = "https://images-api.nasa.gov/search?media_type=image&page_size=100&q=" + Http.encode(topic)
        val items = http.getJson(url).jsonObject["collection"]?.jsonObject?.get("items")?.jsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element.jsonObject
            val data = item["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val links = item["links"]?.jsonArray?.map { it.jsonObject } ?: return@mapNotNull null
            // Pick the size closest to a TV screen; skip portraits and anything too small to fill it.
            val sized = links.mapNotNull { link ->
                val w = link.num("width") ?: return@mapNotNull null
                val h = link.num("height") ?: return@mapNotNull null
                link.str("href")?.let { Triple(it, w, h) }
            }
            val best = sized.filter { (_, w, h) -> w >= 1600 && w >= h * 1.3 }.minByOrNull { it.second } ?: return@mapNotNull null
            val caption = listOfNotNull(data.str("title"), data.str("center")).joinToString(" · ") + " · NASA"
            RemoteImage(best.first, caption)
        }.shuffled().take(40)
    }

    private fun plain(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
}
