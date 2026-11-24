package io.github.remdee13.pavlovtv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.CustomFeed
import io.github.remdee13.pavlovtv.net.FeedPageText
import io.github.remdee13.pavlovtv.net.FeedServer
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassDialog
import io.github.remdee13.pavlovtv.ui.components.QrCode
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import kotlinx.coroutines.launch

/** Type a site, a page or an RSS link with the remote; the feed is found automatically. */
@Composable
fun AddFeedDialog(add: suspend (String) -> CustomFeed?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }
    val context = LocalContext.current
    fun submit() {
        val input = text.trim()
        if (input.isEmpty() || checking) return
        checking = true
        status = context.getString(R.string.news_feed_checking)
        scope.launch {
            val feed = add(input)
            checking = false
            status = if (feed != null) {
                text = ""
                context.getString(R.string.news_feed_added, feed.name)
            } else {
                context.getString(R.string.news_feed_not_found)
            }
        }
    }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(600.dp), initialFocus = field) {
        Text(stringResource(R.string.news_add_feed), style = Type.title)
        Text(stringResource(R.string.news_add_feed_hint), style = Type.small, color = Palette.TextTertiary)
        GlassTextField(text, { text = it }, stringResource(R.string.news_feed_placeholder), focusRequester = field, keyboardType = KeyboardType.Uri, onAction = ::submit)
        status?.let { Text(it, style = Type.body, color = if (checking) Palette.TextSecondary else Palette.Accent) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(stringResource(R.string.news_add), ::submit, icon = Icons.Outlined.Add, accent = true)
            GlassButton(stringResource(R.string.action_done), onDismiss)
        }
    }
}

/** Shows a QR code; the phone opens a page where any link can be pasted. */
@Composable
fun PhoneFeedDialog(add: suspend (String) -> CustomFeed?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    DisposableEffect(Unit) {
        val text = FeedPageText(
            title = context.getString(R.string.news_phone_page_title),
            hint = context.getString(R.string.news_phone_page_hint),
            placeholder = context.getString(R.string.news_feed_placeholder),
            add = context.getString(R.string.news_add),
            checking = context.getString(R.string.news_feed_checking),
            added = context.getString(R.string.news_feed_added, "{name}"),
            notFound = context.getString(R.string.news_feed_not_found),
        )
        val server = FeedServer(text) { input ->
            add(input)?.also { feed -> scope.launch { added += feed.name } }
        }
        address = runCatching { server.start(scope) }.getOrNull()
        failed = address == null
        onDispose { server.stop() }
    }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(640.dp)) {
        Text(stringResource(R.string.news_add_from_phone), style = Type.title)
        val url = address
        if (url == null) {
            Text(stringResource(if (failed) R.string.upload_no_network else R.string.weather_loading), style = Type.body, color = Palette.TextSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCode(url, Modifier.size(180.dp))
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.upload_step1), style = Type.body)
                    Text(stringResource(R.string.upload_step2), style = Type.body)
                    Text(stringResource(R.string.news_phone_step3), style = Type.body)
                    Spacer(Modifier.height(4.dp))
                    Text(url, style = Type.small, color = Palette.Accent)
                    added.takeLast(3).forEach { name ->
                        Text(context.getString(R.string.news_feed_added, name), style = Type.small, color = Palette.Warm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        GlassButton(stringResource(R.string.action_done), onDismiss, accent = true)
    }
}
