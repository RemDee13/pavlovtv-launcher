package io.github.remdee13.pavlovtv.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.NewsItem
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.newsTime
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type

private val CardShape = RoundedCornerShape(18.dp)

/**
 * All headlines as picture cards. OK opens the story in the built-in reader; [read] opens
 * the reader right away (from the home widget), and Back from it lands on this grid.
 */
@Composable
fun NewsScreen(vm: LauncherViewModel, startIndex: Int, read: Boolean, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val items by vm.news.collectAsState()
    val loading by vm.newsLoading.collectAsState()
    val start = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    var focusIndex by remember { mutableIntStateOf(start) }
    var reading by remember { mutableStateOf(if (read && items.isNotEmpty()) start else null) }
    var refocus by remember { mutableIntStateOf(0) }
    var focusedCard by remember { mutableIntStateOf(-1) }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = start)
    val cardFocus = remember { FocusRequester() }
    val now by rememberNow(60_000L)

    ScreenLayer(onBack = onBack, scrim = Color.Black.copy(alpha = 0.55f), initialFocus = if (items.isNotEmpty()) cardFocus else null) {
        // Hidden (not removed) while reading, so focus can come back to the same card.
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = if (reading == null) 1f else 0f }.padding(horizontal = 40.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.news_title), style = Type.clock.copy(fontSize = 30.sp), modifier = Modifier.weight(1f))
                GlassButton(stringResource(R.string.action_refresh), { vm.refreshNews() }, icon = Icons.Outlined.Refresh, focusRequester = if (items.isEmpty()) cardFocus else null)
                Spacer(Modifier.width(10.dp))
                GlassButton(stringResource(R.string.news_sources), onOpenSettings, icon = Icons.Outlined.Tune)
            }
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(stringResource(if (loading) R.string.news_loading else R.string.news_empty), style = Type.body, color = Palette.TextSecondary)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 28.dp),
            ) {
                itemsIndexed(items, key = { i, item -> "$i${item.link}" }) { index, item ->
                    NewsCard(
                        item = item,
                        now = now,
                        preview = vm::previewImage,
                        focusRequester = if (index == focusIndex) cardFocus else null,
                        onFocus = { focused ->
                            if (focused) focusIndex = index
                            focusedCard = if (focused) index else if (focusedCard == index) -1 else focusedCard
                        },
                        onClick = {
                            focusIndex = index
                            reading = index
                        },
                    )
                }
            }
        }
        reading?.let { index ->
            val item = items.getOrNull(index)
            if (item != null) {
                ArticleReader(
                    item = item,
                    load = vm::article,
                    hasPrevious = index > 0,
                    hasNext = index < items.lastIndex,
                    onPrevious = { reading = index - 1 },
                    onNext = { reading = index + 1 },
                    onBack = {
                        focusIndex = index
                        reading = null
                        refocus++
                    },
                )
            }
        }
    }
    LaunchedEffect(refocus) {
        if (refocus == 0) return@LaunchedEffect
        gridState.scrollToItem(focusIndex)
        repeat(40) {
            withFrameNanos { }
            if (focusedCard == focusIndex) return@LaunchedEffect
            runCatching { cardFocus.requestFocus() }
        }
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    now: Long,
    preview: suspend (String) -> String?,
    focusRequester: FocusRequester?,
    onFocus: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val image by produceState(item.image, item.link) {
        value = item.image
        if (value == null) value = preview(item.link)
    }
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = CardShape,
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        rimOnTop = true,
        onFocus = onFocus,
    ) {
        Box(Modifier.fillMaxSize().clip(CardShape).background(placeholder(item.source))) {
            image?.let { url ->
                AsyncImage(
                    model = remember(url) { ImageRequest.Builder(context).data(url).size(560, 316).crossfade(200).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    "${item.source} · ${newsTime(context, item.time, now, locale)}",
                    style = Type.tiny,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.title,
                    style = Type.body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Calm color per source, shown while a picture loads or when there is none. */
private fun placeholder(source: String): Brush {
    val hue = ((source.hashCode() % 360) + 360) % 360
    val top = Color.hsl(hue.toFloat(), 0.35f, 0.32f)
    val bottom = Color.hsl(((hue + 40) % 360).toFloat(), 0.4f, 0.16f)
    return Brush.linearGradient(listOf(top, bottom))
}
