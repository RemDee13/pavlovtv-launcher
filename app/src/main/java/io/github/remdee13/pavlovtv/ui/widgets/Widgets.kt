package io.github.remdee13.pavlovtv.ui.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.NewsItem
import io.github.remdee13.pavlovtv.ui.amPm
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.conditionRes
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.formatTemp
import io.github.remdee13.pavlovtv.ui.newsTime
import io.github.remdee13.pavlovtv.ui.offsetHint
import io.github.remdee13.pavlovtv.ui.shortDay
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.timeText
import io.github.remdee13.pavlovtv.ui.zoneOrDefault
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

val WidgetHeight = 172.dp
internal val WidgetShape = RoundedCornerShape(26.dp)

private class Star(val x: Float, val y: Float, val size: Float)

private val Stars = listOf(
    Star(-0.46f, -0.48f, 0.026f), Star(0.36f, -0.56f, 0.02f), Star(0.58f, -0.12f, 0.017f),
    Star(-0.62f, 0.04f, 0.02f), Star(0.08f, -0.72f, 0.018f), Star(-0.22f, -0.26f, 0.014f), Star(0.5f, 0.32f, 0.015f),
)

/** Analog clock whose face is the sky at that place right now. */
@Composable
fun SkyDial(time: ZonedDateTime, sky: Sky, modifier: Modifier = Modifier, showSeconds: Boolean = true) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(Brush.verticalGradient(listOf(sky.top, sky.bottom), startY = center.y - r, endY = center.y + r), r)
        if (sky.night > 0f) {
            Stars.forEach { star -> drawCircle(Color.White.copy(alpha = 0.85f * sky.night), r * star.size, center + Offset(star.x * r, star.y * r)) }
        }
        // Sun or moon in the lower half of the dial.
        val glyph = center + Offset(0f, r * 0.44f)
        if (sky.sunUp) {
            drawCircle(Color(0x55FFD27A), r * 0.17f, glyph)
            drawCircle(Color(0xFFFFC857), r * 0.1f, glyph)
        } else {
            drawCircle(Color(0xFFF4F1E4), r * 0.11f, glyph)
            drawCircle(lerp(sky.top, sky.bottom, 0.72f), r * 0.1f, glyph + Offset(r * 0.055f, -r * 0.035f))
        }
        drawCircle(Color.White.copy(alpha = 0.38f), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val outer = r * 0.88f
            val inner = if (i % 3 == 0) r * 0.7f else r * 0.8f
            drawLine(
                sky.ink.copy(alpha = if (i % 3 == 0) 0.85f else 0.35f),
                center + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                center + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
                strokeWidth = if (i % 3 == 0) r * 0.06f else r * 0.035f,
                cap = StrokeCap.Round,
            )
        }
        val seconds = time.second
        val minutes = time.minute + seconds / 60f
        val hours = time.hour % 12 + minutes / 60f
        hand(hours * 30f, r * 0.48f, r * 0.09f, sky.ink)
        hand(minutes * 6f, r * 0.72f, r * 0.06f, sky.ink)
        val accent = if (sky.sunUp) Color(0xFFFF6B3D) else Palette.Warm
        if (showSeconds) hand(seconds * 6f, r * 0.8f, r * 0.025f, accent, tail = r * 0.18f)
        drawCircle(accent, r * 0.07f)
    }
}

private fun DrawScope.hand(angle: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    val a = Math.toRadians(angle - 90.0)
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    drawLine(color, center - dir * tail, center + dir * length, strokeWidth = width, cap = StrokeCap.Round)
}

/**
 * Rotating headline. With a picture the card becomes the picture, with the title on a dark
 * gradient at the bottom; feeds without pictures get the page's own preview image.
 */
@Composable
fun NewsWidget(
    item: NewsItem?,
    index: Int,
    total: Int,
    loading: Boolean,
    preview: suspend (String) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // produceState keeps the old value when the story changes: start from this story's own picture.
    val image by produceState(item?.image, item?.link) {
        value = item?.image
        if (value == null && item != null) value = preview(item.link)
    }
    GlassCard(onClick = onClick, modifier = modifier.height(WidgetHeight), shape = WidgetShape, rimOnTop = image != null) {
        AnimatedContent(
            targetState = item to image,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "headline",
            modifier = Modifier.fillMaxSize(),
        ) { (current, picture) ->
            if (current != null && picture != null) {
                PictureHeadline(current, picture, index, total)
            } else {
                TextHeadline(current, index, total, loading)
            }
        }
    }
}

@Composable
private fun PictureHeadline(item: NewsItem, image: String, index: Int, total: Int) {
    val context = LocalContext.current
    val locale = currentLocale()
    val now by rememberNow(60_000L)
    Box(Modifier.fillMaxSize().clip(WidgetShape).background(Color(0xFF1A2030))) {
        AsyncImage(
            model = remember(image) { ImageRequest.Builder(context).data(image).size(720, 344).crossfade(250).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.28f to Color.Transparent,
                    0.42f to Color.Black.copy(alpha = 0.1f),
                    1f to Color.Black.copy(alpha = 0.88f),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            NewsHeader(item.source)
            Spacer(Modifier.weight(1f))
            Text(
                item.title,
                style = Type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            NewsFooter(newsTime(context, item.time, now, locale), index, total)
        }
    }
}

@Composable
private fun TextHeadline(item: NewsItem?, index: Int, total: Int, loading: Boolean) {
    val context = LocalContext.current
    val locale = currentLocale()
    val now by rememberNow(60_000L)
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        NewsHeader(item?.source)
        Spacer(Modifier.height(10.dp))
        Text(
            item?.title ?: stringResource(if (loading) R.string.news_loading else R.string.news_empty),
            style = if (item != null) Type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp) else Type.body,
            color = if (item != null) Palette.TextPrimary else Palette.TextSecondary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        NewsFooter(newsTime(context, item?.time, now, locale), index, total)
    }
}

@Composable
private fun NewsHeader(source: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp), tint = Palette.Accent)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.news_title), style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary)
        Spacer(Modifier.weight(1f))
        if (source != null) Text(source, style = Type.tiny, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun NewsFooter(time: String, index: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = Type.tiny, color = Palette.TextSecondary)
        Spacer(Modifier.weight(1f))
        if (total > 1) {
            val dots = total.coerceAtMost(6)
            repeat(dots) { i ->
                Box(
                    Modifier.padding(start = 4.dp).size(5.dp)
                        .background(if (i == index % dots) Color.White else Color.White.copy(alpha = 0.3f), CircleShape),
                )
            }
        }
    }
}
