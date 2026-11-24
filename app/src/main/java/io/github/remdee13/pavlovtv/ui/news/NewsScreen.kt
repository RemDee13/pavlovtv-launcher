package io.github.remdee13.pavlovtv.ui.news

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.GlassPanel
import io.github.remdee13.pavlovtv.ui.components.QrCode
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.newsTime
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type

@Composable
fun NewsScreen(vm: LauncherViewModel, startIndex: Int, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val items by vm.news.collectAsState()
    val loading by vm.newsLoading.collectAsState()
    var selected by remember { mutableIntStateOf(startIndex.coerceAtLeast(0)) }
    val context = LocalContext.current
    val locale = currentLocale()
    val now by rememberNow(60_000L)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val startFocus = remember { FocusRequester() }

    ScreenLayer(onBack = onBack, scrim = Color.Black.copy(alpha = 0.5f), initialFocus = if (items.isNotEmpty()) startFocus else null) {
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(400.dp).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.news_title), style = Type.clock.copy(fontSize = 30.sp), modifier = Modifier.weight(1f))
                    GlassButton(stringResource(R.string.action_refresh), { vm.refreshNews() }, icon = Icons.Outlined.Refresh)
                    Spacer(Modifier.width(10.dp))
                    GlassButton(stringResource(R.string.news_sources), onOpenSettings, icon = Icons.Outlined.Tune)
                }
                Spacer(Modifier.height(16.dp))
                if (items.isEmpty()) {
                    Text(stringResource(if (loading) R.string.news_loading else R.string.news_empty), style = Type.body, color = Palette.TextSecondary)
                }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)) {
                    itemsIndexed(items, key = { i, item -> "$i${item.link}" }) { index, item ->
                        GlassCard(
                            onClick = { openLink(context, item.link) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            focusedScale = 1.02f,
                            focusRequester = if (index == startIndex.coerceIn(0, items.lastIndex)) startFocus else null,
                            onFocus = { if (it) selected = index },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column {
                                Text(item.title, style = Type.body.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("${item.source} · ${newsTime(context, item.time, now, locale)}", style = Type.tiny, color = Palette.TextTertiary)
                            }
                        }
                    }
                }
            }

            val item = items.getOrNull(selected)
            GlassPanel(Modifier.weight(1f).fillMaxHeight(), strength = 1f) {
                if (item != null) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        if (item.image != null) {
                            AsyncImage(
                                model = item.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        Text("${item.source} · ${newsTime(context, item.time, now, locale)}", style = Type.small, color = Palette.Accent)
                        Spacer(Modifier.height(6.dp))
                        Text(item.title, style = Type.title.copy(fontSize = 21.sp, lineHeight = 27.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text(item.summary, style = Type.body.copy(lineHeight = 21.sp), color = Palette.TextSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (item.link.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QrCode(item.link, Modifier.size(96.dp))
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(stringResource(R.string.news_qr_hint), style = Type.small, color = Palette.TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.news_open_hint), style = Type.tiny, color = Palette.TextTertiary)
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
    LaunchedEffect(items.size) { if (selected >= items.size) selected = 0 }
}

private fun openLink(context: android.content.Context, link: String) {
    if (link.isEmpty()) return
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) Toast.makeText(context, R.string.news_no_browser, Toast.LENGTH_LONG).show()
}
