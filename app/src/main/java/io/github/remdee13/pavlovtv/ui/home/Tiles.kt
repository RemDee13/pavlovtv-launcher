package io.github.remdee13.pavlovtv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AppEntry
import io.github.remdee13.pavlovtv.data.RecProgram
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type

val TileWidth = 160.dp
val TileHeight = 90.dp
private val TileShape = RoundedCornerShape(16.dp)

@Composable
private fun MoveArrow(icon: ImageVector, modifier: Modifier) {
    Box(modifier.size(28.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color(0xFF0D1526), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun AppTile(
    app: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TileWidth,
    focusRequester: FocusRequester? = null,
    /** Picked up in move mode: arrows on both sides and a hint instead of the label. */
    moving: Boolean = false,
) {
    val cache = LocalAppVisuals.current
    val density = LocalDensity.current
    val bannerW = with(density) { width.roundToPx() }
    val bannerH = with(density) { (width * 9f / 16f).roundToPx() }
    val iconPx = with(density) { 48.dp.roundToPx() }
    val visual by produceState(cache.peek(app.key), app.key) {
        value = cache.load(app, bannerW, bannerH, iconPx)
    }
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(contentAlignment = Alignment.Center) {
        GlassCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(width, width * 9f / 16f),
            shape = TileShape,
            focusedScale = 1.1f,
            focusRequester = focusRequester,
            rimOnTop = true,
            onFocus = { focused = it },
        ) {
            val v = visual
            val banner = v?.banner
            if (banner != null) {
                Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(TileShape))
            } else {
                val tint = v?.tint ?: Color(0xFF2A3446)
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(TileShape)
                        .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.92f), tint.copy(alpha = 0.55f))))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    v?.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, style = Type.small.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (moving) {
            MoveArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart).offset(x = (-24).dp))
            MoveArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd).offset(x = 24.dp))
        }
      }
        Spacer(Modifier.height(6.dp))
        Text(
            if (moving) stringResource(R.string.app_move_hint) else app.label,
            style = Type.small,
            color = if (moving) Palette.Accent else Palette.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (focused || moving) 1f else 0f),
        )
    }
}

val RecHeight = 112.dp

@Composable
fun RecommendationCard(program: RecProgram, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val width = (RecHeight * program.aspect.coerceIn(0.6f, 1.8f))
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.width(width)) {
        GlassCard(
            onClick = onClick,
            modifier = Modifier.size(width, RecHeight),
            shape = TileShape,
            focusedScale = 1.08f,
            rimOnTop = true,
            onFocus = { focused = it },
        ) {
            Box(Modifier.fillMaxSize().clip(TileShape).background(Color.White.copy(alpha = 0.06f))) {
                if (program.image != null) {
                    AsyncImage(
                        model = remember(program.image) { ImageRequest.Builder(context).data(program.image).crossfade(true).build() },
                        contentDescription = program.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(program.title, style = Type.small, modifier = Modifier.align(Alignment.Center).padding(8.dp), maxLines = 3, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            program.title,
            style = Type.small,
            color = Palette.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (focused) 1f else 0f),
        )
    }
}
