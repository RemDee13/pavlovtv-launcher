package io.github.remdee13.pavlovtv.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.util.Collections

/** A piece of an article as shown by the TV reader. */
sealed interface ArticleBlock {
    data class Paragraph(val text: String) : ArticleBlock
    data class Heading(val text: String) : ArticleBlock
    data class Quote(val text: String) : ArticleBlock
    data class Bullet(val text: String) : ArticleBlock
    data class Picture(val url: String, val caption: String?) : ArticleBlock
}

data class Article(val title: String, val image: String?, val blocks: List<ArticleBlock>)

/**
 * Loads news pages and turns them into plain readable text for the TV, like a browser's
 * reading mode: menus, ads, share buttons and comments are dropped.
 */
class ArticleRepository(private val http: Http) {
    private val articles = LruCache<String, Article>(8)
    private val previews = LruCache<String, String>(300)
    private val noPreview: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val previewGate = Semaphore(3)

    suspend fun load(url: String): Article? {
        if (url.isEmpty()) return null
        articles.get(url)?.let { return it }
        val (bytes, charset) = runCatching { http.getPrefix(url, MAX_PAGE) }.getOrNull() ?: return null
        val article = withContext(Dispatchers.Default) {
            runCatching { ArticleExtractor.extract(bytes, charset, url) }.getOrNull()
        } ?: return null
        articles.put(url, article)
        return article
    }

    /** The page's own preview picture (og:image), for feeds that send no image. */
    suspend fun previewImage(url: String): String? {
        if (url.isEmpty()) return null
        previews.get(url)?.let { return it }
        if (url in noPreview) return null
        return previewGate.withPermit {
            previews.get(url)?.let { return@withPermit it }
            val image = runCatching {
                val (bytes, charset) = http.getPrefix(url, HEAD_BYTES)
                withContext(Dispatchers.Default) { ArticleExtractor.metaImage(ArticleExtractor.parse(bytes, charset, url)) }
            }.getOrNull()
            if (image != null) previews.put(url, image) else noPreview += url
            image
        }
    }

    private companion object {
        const val MAX_PAGE = 3L * 1024 * 1024
        const val HEAD_BYTES = 160L * 1024
    }
}

internal object ArticleExtractor {
    private val junk = Regex(
        "comment|share|social|related|recommend|promo|subscri|newsletter|sidebar|advert|\\bads?\\b|ad-|banner|cookie|" +
            "popup|modal|breadcrumb|footer|masthead|menu|\\bnav|\\btags?\\b|author-?bio|read-?more|more-stories|outbrain|" +
            "taboola|paywall|signup|sponsor|widget|toolbar|pagination|rating|vote",
        RegexOption.IGNORE_CASE,
    )
    private val strongJunk = Regex("comment|share|social|related|subscri|newsletter|advert|cookie|outbrain|taboola|sponsor", RegexOption.IGNORE_CASE)
    private val positive = Regex("article|content|entry|main|body|story|text|post|news", RegexOption.IGNORE_CASE)
    private val badImage = Regex("pixel|tracking|logo|avatar|icon|sprite|spacer|blank|1x1|emoji|badge", RegexOption.IGNORE_CASE)

    fun parse(bytes: ByteArray, charset: String?, url: String): Document =
        Jsoup.parse(ByteArrayInputStream(bytes), charset, url)

    fun metaImage(doc: Document): String? =
        doc.selectFirst(
            "meta[property=og:image], meta[name=og:image], meta[property=og:image:url], " +
                "meta[name=twitter:image], meta[property=twitter:image], meta[name=twitter:image:src]",
        )?.absUrl("content")?.takeIf { it.startsWith("http") }

    fun extract(bytes: ByteArray, charset: String?, url: String): Article? {
        val doc = parse(bytes, charset, url)
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: doc.title()
        val lead = metaImage(doc)
        doc.select("script, style, noscript, iframe, form, nav, footer, aside, button, svg, template, select, input, [hidden], [aria-hidden=true]").remove()
        for (element in doc.body().allElements.toList()) {
            if (element === doc.body() || element.parent() == null) continue
            val marks = element.className() + " " + element.id()
            if (marks.isBlank()) continue
            if (strongJunk.containsMatchIn(marks) || junk.containsMatchIn(marks) && !positive.containsMatchIn(marks)) element.remove()
        }
        val root = findRoot(doc) ?: return null
        val blocks = ArrayList<ArticleBlock>()
        walk(root, blocks, lead, title.trim())
        val compact = blocks.distinct()
        val textLength = compact.sumOf { (it as? ArticleBlock.Paragraph)?.text?.length ?: 0 }
        if (textLength < MIN_TEXT) return null
        return Article(title.trim(), lead, compact.take(MAX_BLOCKS))
    }

    /** The element that holds the story: <article>, articleBody, or the best-scoring text block. */
    private fun findRoot(doc: Document): Element? {
        doc.selectFirst("[itemprop=articleBody]")?.let { if (paragraphText(it) >= MIN_TEXT) return it }
        doc.select("article").maxByOrNull { paragraphText(it) }?.let { if (paragraphText(it) >= MIN_TEXT * 2) return it }
        val scores = HashMap<Element, Double>()
        for (p in doc.body().select("p, pre")) {
            val text = p.text()
            if (text.length < 40) continue
            val score = 1.0 + text.count { it == ',' || it == '，' } + minOf(text.length / 100.0, 3.0)
            val parent = p.parent() ?: continue
            scores[parent] = (scores[parent] ?: 0.0) + score
            parent.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score / 2 }
        }
        return scores.maxByOrNull { it.value }?.key
    }

    private fun paragraphText(element: Element): Int = element.select("p").sumOf { it.text().length }

    private fun walk(element: Element, out: MutableList<ArticleBlock>, lead: String?, title: String) {
        for (child in element.children()) {
            when (child.tagName()) {
                "p" -> {
                    child.select("img").forEach { picture(it, null, lead)?.let(out::add) }
                    text(child)?.let { out += ArticleBlock.Paragraph(it) }
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> text(child)?.takeIf { it != title }?.let { out += ArticleBlock.Heading(it) }
                "blockquote" -> text(child)?.let { out += ArticleBlock.Quote(it) }
                "ul", "ol" -> child.children().filter { it.tagName() == "li" }.forEach { li -> text(li)?.let { out += ArticleBlock.Bullet(it) } }
                "img" -> picture(child, null, lead)?.let(out::add)
                "figure", "picture" -> {
                    val caption = child.selectFirst("figcaption")?.text()?.takeIf { it.isNotBlank() }
                    child.selectFirst("img")?.let { picture(it, caption, lead) }?.let(out::add)
                }
                "pre" -> child.wholeText().trim().takeIf { it.isNotEmpty() }?.let { out += ArticleBlock.Paragraph(it) }
                "table", "figcaption", "time", "small" -> Unit
                else -> {
                    val hasBlocks = child.children().any { it.tagName() in BLOCK_TAGS }
                    if (hasBlocks) walk(child, out, lead, title) else text(child)?.takeIf { it.length >= 25 }?.let { out += ArticleBlock.Paragraph(it) }
                }
            }
        }
    }

    private fun text(element: Element): String? = element.text().trim().takeIf { it.length >= 2 }

    private fun picture(img: Element, caption: String?, lead: String?): ArticleBlock.Picture? {
        val src = listOf("data-src", "data-lazy-src", "data-original", "src")
            .map { img.absUrl(it) }
            .firstOrNull { it.startsWith("http") }
            ?: img.attr("srcset").split(',').firstOrNull()?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { java.net.URI(img.baseUri()).resolve(it).toString() }.getOrNull() }
            ?: return null
        if (!src.startsWith("http") || src == lead || src.endsWith(".svg") || src.endsWith(".gif") || badImage.containsMatchIn(src)) return null
        val width = img.attr("width").toIntOrNull()
        if (width != null && width < 120) return null
        return ArticleBlock.Picture(src, caption)
    }

    private val BLOCK_TAGS = setOf("p", "div", "section", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote", "figure", "img", "picture", "pre", "article", "main")
    private const val MIN_TEXT = 250
    private const val MAX_BLOCKS = 400
}
