package io.github.remdee13.pavlovtv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.charset.Charset

/**
 * Turns whatever the user typed into a working news feed. Accepts a site ("bbc.com"),
 * any page of it, a YouTube channel, or a direct RSS/Atom link:
 *  1. the address itself may already be a feed;
 *  2. otherwise the page usually announces its feeds in <link rel="alternate">;
 *  3. otherwise the usual feed paths (/feed, /rss, …) are tried.
 */
class FeedFinder(private val http: Http) {

    suspend fun find(input: String): CustomFeed? {
        val start = normalize(input) ?: return null
        val page = runCatching { http.getBytes(start.toString()) }.getOrNull()
        if (page != null) {
            val (bytes, charset) = page
            asFeed(start, bytes, charset)?.let { return it }
            val html = String(bytes, charset?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8)
            for (link in announcedFeeds(html, start).take(MAX_ANNOUNCED)) {
                load(link)?.let { return it }
            }
        }
        val root = start.newBuilder().encodedPath("/").query(null).fragment(null).build()
        return coroutineScope {
            COMMON_PATHS.map { path -> async { root.resolve(path)?.let { load(it) } } }.awaitAll().firstOrNull { it != null }
        }
    }

    private suspend fun load(url: HttpUrl): CustomFeed? {
        val (bytes, charset) = runCatching { http.getBytes(url.toString()) }.getOrNull() ?: return null
        return asFeed(url, bytes, charset)
    }

    private fun asFeed(url: HttpUrl, bytes: ByteArray, charset: String?): CustomFeed? {
        if (!looksLikeFeed(bytes)) return null
        val items = runCatching { RssParser.parse(bytes, charset, "") }.getOrDefault(emptyList())
        if (items.isEmpty()) return null
        val title = runCatching { RssParser.feedTitle(bytes, charset) }.getOrNull()?.takeIf { it.isNotBlank() }
        return CustomFeed(name = (title ?: url.host.removePrefix("www.")).take(60), url = url.toString())
    }

    private fun looksLikeFeed(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1).lowercase()
        return "<rss" in head || "<feed" in head || "<rdf:rdf" in head
    }

    private fun normalize(input: String): HttpUrl? {
        val text = input.trim().trimEnd('/').takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
        val withScheme = if (text.startsWith("http://", true) || text.startsWith("https://", true)) text else "https://$text"
        return withScheme.toHttpUrlOrNull()?.takeIf { '.' in it.host }
    }

    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val feedType = Regex("type\\s*=\\s*[\"']?application/(rss|atom)\\+xml", RegexOption.IGNORE_CASE)
    private val href = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    private fun announcedFeeds(html: String, base: HttpUrl): List<HttpUrl> =
        linkTag.findAll(html.take(MAX_HTML)).map { it.value }
            .filter { feedType.containsMatchIn(it) }
            .mapNotNull { tag -> href.find(tag)?.groupValues?.get(1)?.replace("&amp;", "&") }
            .mapNotNull { base.resolve(it) }
            .distinct()
            .toList()

    private companion object {
        const val MAX_ANNOUNCED = 4
        const val MAX_HTML = 400_000
        val COMMON_PATHS = listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/index.xml", "/atom.xml")
    }
}
