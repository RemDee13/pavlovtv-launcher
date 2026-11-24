package io.github.remdee13.pavlovtv.data

import android.content.Context
import android.util.Xml
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class NewsTopic { AI, TECH, BUSINESS, SCIENCE, WORLD }

data class NewsSource(
    val id: String,
    val name: String,
    val url: String,
    val language: String,
    val topic: NewsTopic = NewsTopic.WORLD,
)

/**
 * Ready-made feeds to pick from, grouped by topic. All of them were checked to serve valid
 * RSS without a key. They are only suggestions: any site or RSS link can be added in
 * settings (see [FeedFinder]).
 */
object NewsSources {
    private fun ai(id: String, name: String, url: String, lang: String) = NewsSource(id, name, url, lang, NewsTopic.AI)
    private fun tech(id: String, name: String, url: String, lang: String) = NewsSource(id, name, url, lang, NewsTopic.TECH)
    private fun biz(id: String, name: String, url: String, lang: String) = NewsSource(id, name, url, lang, NewsTopic.BUSINESS)
    private fun sci(id: String, name: String, url: String, lang: String) = NewsSource(id, name, url, lang, NewsTopic.SCIENCE)

    val all = listOf(
        // AI
        ai("verge_ai", "The Verge · AI", "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml", "en"),
        ai("techcrunch_ai", "TechCrunch · AI", "https://techcrunch.com/category/artificial-intelligence/feed/", "en"),
        ai("mit_ai", "MIT Technology Review · AI", "https://www.technologyreview.com/topic/artificial-intelligence/feed", "en"),
        ai("habr_ai", "Хабр · Искусственный интеллект", "https://habr.com/ru/rss/hubs/artificial_intelligence/articles/", "ru"),
        ai("habr_ml", "Хабр · Машинное обучение", "https://habr.com/ru/rss/hubs/machine_learning/articles/", "ru"),
        // Technology
        tech("verge", "The Verge", "https://www.theverge.com/rss/index.xml", "en"),
        tech("ars", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "en"),
        tech("techcrunch", "TechCrunch", "https://techcrunch.com/feed/", "en"),
        tech("engadget", "Engadget", "https://www.engadget.com/rss.xml", "en"),
        tech("habr", "Хабр", "https://habr.com/ru/rss/articles/", "ru"),
        tech("3dnews", "3DNews", "https://3dnews.ru/news/rss/", "ru"),
        tech("ixbt", "iXBT", "https://www.ixbt.com/export/news.rss", "ru"),
        tech("cnews", "CNews", "https://www.cnews.ru/inc/rss/news.xml", "ru"),
        tech("xataka", "Xataka", "https://www.xataka.com/feedburner.xml", "es"),
        tech("webtekno", "Webtekno", "https://www.webtekno.com/rss.xml", "tr"),
        tech("heise", "heise online", "https://www.heise.de/rss/heise-atom.xml", "de"),
        tech("numerama", "Numerama", "https://www.numerama.com/feed/", "fr"),
        tech("tecnoblog", "Tecnoblog", "https://tecnoblog.net/feed/", "pt"),
        // Business
        biz("bbc_business", "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "en"),
        biz("cnbc", "CNBC", "https://www.cnbc.com/id/10001147/device/rss/rss.html", "en"),
        biz("npr_business", "NPR Business", "https://feeds.npr.org/1006/rss.xml", "en"),
        biz("rbc", "РБК", "https://rssexport.rbc.ru/rbcnews/news/30/full.rss", "ru"),
        biz("kommersant_biz", "Коммерсантъ · Бизнес", "https://www.kommersant.ru/RSS/section-business.xml", "ru"),
        biz("vc", "vc.ru", "https://vc.ru/rss/all", "ru"),
        biz("expansion", "Expansión", "https://www.expansion.com/rss/portada.xml", "es"),
        biz("bloomberght", "Bloomberg HT", "https://www.bloomberght.com/rss", "tr"),
        biz("tagesschau_wirtschaft", "tagesschau · Wirtschaft", "https://www.tagesschau.de/wirtschaft/index~rss2.xml", "de"),
        biz("infomoney", "InfoMoney", "https://www.infomoney.com.br/feed/", "pt"),
        // Science
        sci("bbc_science", "BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "en"),
        sci("nplus1", "N + 1", "https://nplus1.ru/rss", "ru"),
        sci("naked_science", "Naked Science", "https://naked-science.ru/feed", "ru"),
        // World
        NewsSource("bbc_world", "BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml", "en"),
        NewsSource("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "en"),
        NewsSource("npr_world", "NPR World", "https://feeds.npr.org/1004/rss.xml", "en"),
        NewsSource("dw_en", "DW", "https://rss.dw.com/rdf/rss-en-world", "en"),
        NewsSource("france24_en", "France 24", "https://www.france24.com/en/rss", "en"),
        NewsSource("euronews_en", "Euronews", "https://www.euronews.com/rss", "en"),
        NewsSource("bbc_ru", "BBC Русская служба", "https://feeds.bbci.co.uk/russian/rss.xml", "ru"),
        NewsSource("dw_ru", "DW на русском", "https://rss.dw.com/rdf/rss-ru-all", "ru"),
        NewsSource("bbc_mundo", "BBC Mundo", "https://feeds.bbci.co.uk/mundo/rss.xml", "es"),
        NewsSource("dw_es", "DW Español", "https://rss.dw.com/rdf/rss-sp-all", "es"),
        NewsSource("elpais", "El País Internacional", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/internacional/portada", "es"),
        NewsSource("bbc_tr", "BBC Türkçe", "https://feeds.bbci.co.uk/turkce/rss.xml", "tr"),
        NewsSource("dw_tr", "DW Türkçe", "https://rss.dw.com/rdf/rss-tur-all", "tr"),
        NewsSource("tagesschau", "tagesschau", "https://www.tagesschau.de/xml/rss2/", "de"),
        NewsSource("dw_de", "DW Deutsch", "https://rss.dw.com/rdf/rss-de-all", "de"),
        NewsSource("france24_fr", "France 24", "https://www.france24.com/fr/rss", "fr"),
        NewsSource("lemonde", "Le Monde International", "https://www.lemonde.fr/international/rss_full.xml", "fr"),
        NewsSource("bbc_br", "BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml", "pt"),
        NewsSource("dw_br", "DW Brasil", "https://rss.dw.com/rdf/rss-br-all", "pt"),
    )

    /** What a new user sees first. Russian starts with technology news, the rest with world news. */
    fun defaultsFor(language: String): List<String> {
        if (language == "ru") return listOf("habr", "3dnews")
        val own = all.filter { it.language == language && it.topic == NewsTopic.WORLD }.take(2).map { it.id }
        return own.ifEmpty { listOf("bbc_world", "aljazeera") }
    }

    /** Feeds of a topic in the user's language, or in English when there are none. */
    fun forTopic(topic: NewsTopic, language: String): List<NewsSource> {
        val inTopic = all.filter { it.topic == topic }
        return inTopic.filter { it.language == language }.ifEmpty { inTopic.filter { it.language == "en" } }
    }

    /** Catalog shown in settings: the user's language first, then English. */
    fun catalog(language: String): List<NewsSource> =
        all.filter { it.language == language } + if (language == "en") emptyList() else all.filter { it.language == "en" }

    fun byId(id: String): NewsSource? = all.find { it.id == id }
}

@Serializable
data class NewsItem(
    val title: String,
    val summary: String,
    val link: String,
    val image: String? = null,
    val source: String,
    val time: Long? = null,
)

@Serializable
private data class NewsCache(val key: String, val fetchedAt: Long, val items: List<NewsItem>)

class NewsRepository(context: Context, private val http: Http) {
    private val cacheFile = File(context.filesDir, "news.json")
    private val mutex = Mutex()
    private var cache: NewsCache? = runCatching { Http.json.decodeFromString<NewsCache>(cacheFile.readText()) }.getOrNull()
    private val _items = MutableStateFlow(cache?.items ?: emptyList())
    val items: StateFlow<List<NewsItem>> = _items
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun activeSources(settings: LauncherSettings, language: String): List<NewsSource> {
        val ids = settings.newsSources ?: NewsSources.defaultsFor(language)
        val builtIn = ids.mapNotNull { NewsSources.byId(it) }
        val custom = settings.customFeeds.mapIndexed { i, f -> NewsSource("custom_$i", f.name, f.url, language) }
        return builtIn + custom
    }

    suspend fun refresh(settings: LauncherSettings, language: String, force: Boolean = false) = mutex.withLock {
        val sources = activeSources(settings, language)
        val key = sources.joinToString(";") { it.url }
        val current = cache
        if (!force && current != null && current.key == key && System.currentTimeMillis() - current.fetchedAt < MAX_AGE_MS) {
            return@withLock
        }
        if (sources.isEmpty()) {
            _items.value = emptyList()
            return@withLock
        }
        _loading.value = true
        try {
            val fetched = coroutineScope {
                sources.map { source -> async { runCatching { fetch(source) }.getOrDefault(emptyList()) } }.awaitAll()
            }
            // Interleave sources so one busy feed does not push the others out, then sort by time.
            val merged = fetched.flatMap { it.take(PER_SOURCE) }
                .distinctBy { it.title.lowercase() }
                .sortedByDescending { it.time ?: 0L }
                .take(MAX_ITEMS)
            if (merged.isNotEmpty() || current?.key != key) {
                val newCache = NewsCache(key, System.currentTimeMillis(), merged)
                cache = newCache
                _items.value = merged
                withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(Http.json.encodeToString(newCache)) } }
            }
        } finally {
            _loading.value = false
        }
    }

    private suspend fun fetch(source: NewsSource): List<NewsItem> {
        val (bytes, charset) = http.getBytes(source.url)
        return withContext(Dispatchers.Default) { RssParser.parse(bytes, charset, source.name) }
    }

    companion object {
        private const val MAX_AGE_MS = 20 * 60 * 1000L
        private const val PER_SOURCE = 25
        private const val MAX_ITEMS = 60
    }
}

/** Small RSS 2.0 / RSS 1.0 (RDF) / Atom reader built on the platform XmlPullParser. */
object RssParser {
    private class Draft {
        var title: String? = null
        var link: String? = null
        var guid: String? = null
        var description: String? = null
        var date: String? = null
        var image: String? = null
    }

    fun parse(bytes: ByteArray, charset: String?, sourceName: String): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), charset)
        val result = mutableListOf<NewsItem>()
        var draft: Draft? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            try {
                if (event == XmlPullParser.START_TAG) {
                    val name = parser.name.lowercase(Locale.ROOT)
                    val d = draft
                    if (name == "item" || name == "entry") {
                        draft = Draft()
                    } else if (d != null) {
                        when (name) {
                            "title" -> d.title = parser.nextText()
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if ((rel == null || rel == "alternate") && d.link == null) d.link = href
                                } else {
                                    d.link = parser.nextText()
                                }
                            }
                            "guid", "id" -> d.guid = parser.nextText()
                            "description", "summary" -> if (d.description.isNullOrBlank()) d.description = parser.nextText()
                            "content:encoded", "content" -> {
                                val text = parser.nextText()
                                if (d.description.isNullOrBlank()) d.description = text
                                if (d.image == null) d.image = firstImage(text)
                            }
                            "pubdate", "dc:date", "published", "updated" -> if (d.date == null) d.date = parser.nextText()
                            "enclosure", "media:content", "media:thumbnail" -> {
                                val url = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type") ?: parser.getAttributeValue(null, "medium") ?: ""
                                val looksLikeImage = type.startsWith("image") || type.isEmpty() && url?.let(::looksLikeImageUrl) == true
                                if (url != null && looksLikeImage && (d.image == null || name == "media:content")) d.image = url
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val name = parser.name.lowercase(Locale.ROOT)
                    val d = draft
                    if ((name == "item" || name == "entry") && d != null) {
                        build(d, sourceName)?.let(result::add)
                        draft = null
                    }
                }
                event = parser.next()
            } catch (e: Exception) {
                // Malformed fragment (e.g. XHTML inside <content>): skip it and keep going.
                event = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
            }
        }
        return result
    }

    /** The feed's own name: the first <title> before any item or entry. */
    fun feedTitle(bytes: ByteArray, charset: String?): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), charset)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "item", "entry" -> return null
                    "title" -> return clean(parser.nextText()).takeIf { it.isNotBlank() }
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun build(d: Draft, sourceName: String): NewsItem? {
        val title = clean(d.title ?: return null).takeIf { it.isNotBlank() } ?: return null
        val link = d.link?.trim()?.takeIf { it.startsWith("http") } ?: d.guid?.trim()?.takeIf { it.startsWith("http") } ?: ""
        val rawDescription = d.description.orEmpty()
        val image = (d.image ?: firstImage(rawDescription))?.let(::upgradeImage)
        val summary = clean(rawDescription).let { if (it.length > 420) it.take(417).trimEnd() + "…" else it }
        return NewsItem(title, summary.takeIf { it != title }.orEmpty(), link, image, sourceName, parseDate(d.date))
    }

    private val imgRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private fun firstImage(html: String): String? = imgRegex.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }

    private fun looksLikeImageUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")
    }

    /** BBC sends 240px thumbnails; their CDN serves any width from the same path. */
    private val bbcSize = Regex("/(ace/(?:standard|ws))/\\d+/")
    private fun upgradeImage(url: String): String = url.replace(bbcSize, "/$1/800/")

    private val spaces = Regex("\\s+")
    private fun clean(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            .replace('￼', ' ')
            .replace(spaces, " ")
            .trim()

    private val fallbackFormats = listOf(
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm zzz",
        "d MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd HH:mm:ss",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parseDate(raw: String?): Long? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(text).toEpochMilli() }
        for (format in fallbackFormats) {
            runCatching { return ZonedDateTime.parse(text, format).toInstant().toEpochMilli() }
        }
        return null
    }
}
