package io.github.remdee13.pavlovtv.saver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import io.github.remdee13.pavlovtv.Graph
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.SaverMode
import io.github.remdee13.pavlovtv.data.WallpaperKind
import io.github.remdee13.pavlovtv.data.WeatherBundle
import io.github.remdee13.pavlovtv.data.OnlineSource
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.formatTemp
import io.github.remdee13.pavlovtv.ui.longDate
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.timeText
import io.github.remdee13.pavlovtv.ui.widgets.WeatherIcon
import io.github.remdee13.pavlovtv.wallpaper.Gradients
import io.github.remdee13.pavlovtv.wallpaper.drawGradientPreset
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** One picture of the slideshow: either an image ([data]) or a built-in gradient. */
data class Slide(val data: Any? = null, val gradient: Int = 0, val caption: String = "")

suspend fun loadSlides(graph: Graph, settings: LauncherSettings): List<Slide> {
    val c = settings.screensaver
    suspend fun online(source: OnlineSource) = graph.wallpaper.playlist(source).map { Slide(it.url, caption = it.caption) }
    fun photos() = graph.photos.photos.value.map { Slide(it) }
    val gradients = Gradients.presets.indices.map { Slide(gradient = it) }
    val slides = when (c.mode) {
        SaverMode.ONLINE -> online(c.online)
        SaverMode.MY_PHOTOS -> photos().ifEmpty { online(OnlineSource.BING) }
        SaverMode.WALLPAPER -> when (settings.wallpaper.kind) {
            WallpaperKind.ONLINE -> online(settings.wallpaper.online)
            WallpaperKind.PHOTO -> photos().ifEmpty { listOf(Slide(File(settings.wallpaper.photoPath))) }
            WallpaperKind.URL -> listOf(Slide(settings.wallpaper.url))
            WallpaperKind.GRADIENT -> gradients.drop(settings.wallpaper.gradient) + gradients.take(settings.wallpaper.gradient)
        }
    }
    return slides.ifEmpty { gradients }
}

@Composable
fun Screensaver(slides: List<Slide>, settings: LauncherSettings, weather: WeatherBundle?, modifier: Modifier = Modifier) {
    val c = settings.screensaver
    val intervalMs = c.intervalSec * 1000L
    var index by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(slides) {
        index = 0
        while (slides.size > 1) {
            delay(intervalMs)
            index = (index + 1) % slides.size
        }
    }
    // Warm the cache with the next picture so the cross-fade never shows a blank frame.
    LaunchedEffect(index, slides) {
        val next = slides.getOrNull((index + 1) % slides.size.coerceAtLeast(1))?.data ?: return@LaunchedEffect
        context.imageLoader.enqueue(ImageRequest.Builder(context).data(next).build())
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = index, animationSpec = tween(1600), label = "slide") { i ->
            slides.getOrNull(i)?.let { SlideView(it, c.motion, intervalMs + 1600) }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)),
            ),
        )
        // Shift the overlay a little with every slide so nothing burns into the panel.
        val shift = (index % 4) * 6
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 44.dp).offset(x = shift.dp, y = (-shift).dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (c.showClock) SaverClock(settings.use24h)
            if (c.showWeather) {
                weather?.main?.let { main ->
                    Spacer(Modifier.width(36.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        WeatherIcon(main.now.code, main.now.isDay, Modifier.size(52.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(formatTemp(main.now.temp), style = Type.clock.copy(fontSize = 34.sp))
                            Text(main.place.name, style = Type.date, color = Palette.TextSecondary)
                        }
                    }
                }
            }
        }
        val caption = slides.getOrNull(index)?.caption.orEmpty()
        if (caption.isNotEmpty()) {
            Text(
                caption,
                style = Type.caption,
                color = Palette.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 40.dp).widthIn(max = 380.dp),
            )
        }
    }
}

@Composable
private fun SlideView(slide: Slide, motion: Boolean, durationMs: Long) {
    val scale = remember { Animatable(1f) }
    val direction = remember { if (Math.random() < 0.5) -1f else 1f }
    LaunchedEffect(Unit) {
        if (motion) scale.animateTo(1.12f, tween(durationMs.toInt(), easing = LinearEasing))
    }
    val layer = Modifier.fillMaxSize().graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = (scale.value - 1f) * size.width * 0.25f * direction
    }
    if (slide.data == null) {
        Canvas(layer) { drawGradientPreset(Gradients.get(slide.gradient)) }
    } else {
        AsyncImage(model = slide.data, contentDescription = null, contentScale = ContentScale.Crop, modifier = layer)
    }
}

@Composable
private fun SaverClock(use24h: Boolean) {
    val now by rememberNow(1000L)
    val locale = currentLocale()
    val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    Column {
        Text(timeText(time, use24h, locale), style = Type.clock.copy(fontSize = 84.sp, letterSpacing = (-2).sp))
        Text(longDate(time, locale), style = Type.date.copy(fontSize = 20.sp), color = Palette.TextSecondary)
    }
}
