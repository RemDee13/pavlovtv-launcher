package io.github.remdee13.pavlovtv.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.Article
import io.github.remdee13.pavlovtv.data.ArticleBlock
import io.github.remdee13.pavlovtv.data.NewsItem
import io.github.remdee13.pavlovtv.ui.components.QrCode
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.newsTime
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import kotlinx.coroutines.launch

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val article: Article) : ReaderState
    data object Failed : ReaderState
}

private val ReaderBackground = Color(0xF20A0D14)

private val BodyStyle = Type.body.copy(fontSize = 19.sp, lineHeight = 30.sp, color = Color(0xE6FFFFFF))

/**
 * The story as clean text on the TV. Up/Down scroll, Left/Right go to the previous or next
 * story, Back returns to the headlines. A QR code at the end opens the original on a phone.
 */
@Composable
fun ArticleReader(
    item: NewsItem,
    load: suspend (String) -> Article?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    ScreenLayer(onBack = onBack, scrim = ReaderBackground, initialFocus = focus) {
        // The focused box stays the same across stories; only its content changes.
        val state by produceState<ReaderState>(ReaderState.Loading, item.link) {
            value = ReaderState.Loading
            value = load(item.link)?.let { ReaderState.Ready(it) } ?: ReaderState.Failed
        }
        val scroll = remember(item.link) { ScrollState(0) }
        val scope = rememberCoroutineScope()
        run {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val step = (scroll.viewportSize * 0.7f).takeIf { it > 0 } ?: 400f
                        when (event.key) {
                            Key.DirectionDown, Key.PageDown -> { scope.launch { scroll.animateScrollBy(step) }; true }
                            Key.DirectionUp, Key.PageUp -> { scope.launch { scroll.animateScrollBy(-step) }; true }
                            Key.DirectionLeft -> { if (hasPrevious) onPrevious(); true }
                            Key.DirectionRight -> { if (hasNext) onNext(); true }
                            else -> false
                        }
                    }
                    .focusable(),
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(760.dp)
                        .fillMaxHeight()
                        .verticalScroll(scroll)
                        .padding(top = 36.dp, bottom = 64.dp),
                ) {
                    Story(item, state)
                }
                ScrollBar(scroll.value, scroll.maxValue, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
                // Text fades out under the key hint instead of running into it.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, ReaderBackground))),
                )
                Text(
                    stringResource(R.string.article_keys),
                    style = Type.tiny,
                    color = Palette.TextTertiary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun Story(item: NewsItem, state: ReaderState) {
    val context = LocalContext.current
    val locale = currentLocale()
    val now by rememberNow(60_000L)
    val article = (state as? ReaderState.Ready)?.article
    val lead = article?.image ?: item.image
    if (lead != null) {
        AsyncImage(
            model = remember(lead) { ImageRequest.Builder(context).data(lead).size(1520, 856).crossfade(250).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f)),
        )
        Spacer(Modifier.height(22.dp))
    }
    Text("${item.source} · ${newsTime(context, item.time, now, locale)}", style = Type.small, color = Palette.Accent)
    Spacer(Modifier.height(8.dp))
    Text(item.title, style = Type.title.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold))
    Spacer(Modifier.height(20.dp))
    when (state) {
        ReaderState.Loading -> {
            if (item.summary.isNotEmpty()) Text(item.summary, style = BodyStyle)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.article_loading), style = Type.body, color = Palette.TextTertiary)
        }
        ReaderState.Failed -> {
            if (item.summary.isNotEmpty()) Text(item.summary, style = BodyStyle)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.article_failed), style = Type.body, color = Palette.Warm)
        }
        is ReaderState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.article.blocks.forEach { block -> Block(block) }
        }
    }
    if (item.link.isNotEmpty()) {
        Spacer(Modifier.height(36.dp))
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCode(item.link, Modifier.size(112.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(stringResource(R.string.news_qr_hint), style = Type.title)
                Spacer(Modifier.height(4.dp))
                Text(item.link, style = Type.small, color = Palette.TextTertiary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun Block(block: ArticleBlock) {
    val context = LocalContext.current
    when (block) {
        is ArticleBlock.Paragraph -> Text(block.text, style = BodyStyle)
        is ArticleBlock.Heading -> Text(block.text, style = Type.title.copy(fontSize = 23.sp, lineHeight = 30.sp), modifier = Modifier.padding(top = 8.dp))
        is ArticleBlock.Bullet -> Row {
            Text("•", style = BodyStyle, color = Palette.Accent, modifier = Modifier.width(22.dp))
            Text(block.text, style = BodyStyle)
        }
        is ArticleBlock.Quote -> Row {
            Box(Modifier.width(4.dp).height(30.dp).background(Palette.Accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(16.dp))
            Text(block.text, style = BodyStyle.copy(fontStyle = FontStyle.Italic, color = Color.White))
        }
        is ArticleBlock.Picture -> Column {
            AsyncImage(
                model = remember(block.url) { ImageRequest.Builder(context).data(block.url).size(1520, 1200).crossfade(200).build() },
                contentDescription = block.caption,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(16.dp)),
            )
            block.caption?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = Type.small, color = Palette.TextTertiary)
            }
        }
    }
}

@Composable
private fun ScrollBar(value: Int, max: Int, modifier: Modifier) {
    if (max <= 0) return
    val track = 260.dp
    val thumb = 46.dp
    Box(modifier.width(4.dp).height(track).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.dp))) {
        Box(
            Modifier
                .offset(y = (track - thumb) * (value / max.toFloat()))
                .width(4.dp)
                .height(thumb)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
        )
    }
}
