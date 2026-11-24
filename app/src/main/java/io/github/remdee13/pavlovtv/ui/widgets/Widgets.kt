package io.github.remdee13.pavlovtv.ui.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.ClockStyle
import io.github.remdee13.pavlovtv.data.NewsItem
import io.github.remdee13.pavlovtv.data.WeatherBundle
import io.github.remdee13.pavlovtv.data.WorldClock
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
private val WidgetShape = RoundedCornerShape(26.dp)

@Composable
fun WeatherWidget(weather: WeatherBundle?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    GlassCard(onClick = onClick, modifier = modifier.height(WidgetHeight), shape = WidgetShape) {
        val main = weather?.main
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (main == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(code = 2, isDay = true, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (weather == null) R.string.weather_loading else R.string.weather_unavailable), style = Type.body, color = Palette.TextSecondary)
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(code = main.now.code, isDay = main.now.isDay, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(8.dp))
                Text(formatTemp(main.now.temp), style = Type.bigNumber.copy(fontSize = 36.sp), maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
                    Text(main.place.name, style = Type.small.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    main.days.firstOrNull()?.let { today ->
                        Text("↑${formatTemp(today.max)} ↓${formatTemp(today.min)}", style = Type.small, color = Palette.TextSecondary, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(conditionRes(main.now.code)) + " · " + stringResource(R.string.weather_feels_like, formatTemp(main.now.feelsLike)),
                style = Type.small,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val extra = weather?.extra.orEmpty()
                if (extra.isNotEmpty()) {
                    extra.take(3).forEach { report ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(report.place.name, style = Type.tiny, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WeatherIcon(report.now.code, report.now.isDay, Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(formatTemp(report.now.temp), style = Type.small)
                            }
                        }
                    }
                } else {
                    main.days.drop(1).take(4).forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(shortDay(day.date, locale), style = Type.tiny, color = Palette.TextSecondary)
                            WeatherIcon(day.code, true, Modifier.size(22.dp))
                            Text("${formatTemp(day.max)} ${formatTemp(day.min)}", style = Type.tiny, color = Palette.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** Width that fits [count] world clocks side by side. */
fun clocksWidgetWidth(count: Int) = (32 + count.coerceIn(1, 4) * ClockColumn + (count.coerceIn(1, 4) - 1) * 6).dp

private const val ClockColumn = 72

@Composable
fun ClocksWidget(clocks: List<WorldClock>, use24h: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now by rememberNow(1000L)
    val locale = currentLocale()
    val context = LocalContext.current
    val instant = Instant.ofEpochMilli(now)
    GlassCard(onClick = onClick, modifier = modifier.height(WidgetHeight), shape = WidgetShape) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            clocks.take(4).forEach { clock ->
                val zone = zoneOrDefault(clock.place.timezone)
                val time = ZonedDateTime.ofInstant(instant, zone)
                Column(
                    Modifier.width(ClockColumn.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (clock.style == ClockStyle.ANALOG) {
                        AnalogClock(time, Modifier.size(62.dp))
                    } else {
                        Box(Modifier.height(62.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(timeText(time, use24h, locale), style = Type.bigNumber.copy(fontSize = 26.sp), maxLines = 1, softWrap = false)
                                if (!use24h) Text(amPm(time, locale), style = Type.tiny, color = Palette.TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(clock.place.name, style = Type.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(offsetHint(context, zone, instant), style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun AnalogClock(time: ZonedDateTime, modifier: Modifier = Modifier, showSeconds: Boolean = true) {
    val night = time.hour < 6 || time.hour >= 20
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val dial = if (night) listOf(Color(0xFF1E2433), Color(0xFF0D1018)) else listOf(Color(0xF2FFFFFF), Color(0xD9E6ECF5))
        val ink = if (night) Color.White else Color(0xFF1A1F2B)
        drawCircle(Brush.verticalGradient(dial), r)
        drawCircle(Color.White.copy(alpha = if (night) 0.25f else 0.6f), r, style = Stroke(1.2f))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val outer = r * 0.88f
            val inner = if (i % 3 == 0) r * 0.72f else r * 0.8f
            drawLine(
                ink.copy(alpha = if (i % 3 == 0) 0.9f else 0.4f),
                center + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                center + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
                strokeWidth = if (i % 3 == 0) r * 0.06f else r * 0.035f,
                cap = StrokeCap.Round,
            )
        }
        val seconds = time.second
        val minutes = time.minute + seconds / 60f
        val hours = time.hour % 12 + minutes / 60f
        hand(hours * 30f, r * 0.48f, r * 0.085f, ink)
        hand(minutes * 6f, r * 0.7f, r * 0.06f, ink)
        if (showSeconds) hand(seconds * 6f, r * 0.78f, r * 0.025f, Palette.Warm, tail = r * 0.18f)
        drawCircle(Palette.Warm, r * 0.07f)
    }
}

private fun DrawScope.hand(angle: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    val a = Math.toRadians(angle - 90.0)
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    drawLine(color, center - dir * tail, center + dir * length, strokeWidth = width, cap = StrokeCap.Round)
}

@Composable
fun NewsWidget(item: NewsItem?, index: Int, total: Int, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = currentLocale()
    val now by rememberNow(60_000L)
    GlassCard(onClick = onClick, modifier = modifier.height(WidgetHeight), shape = WidgetShape) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 300.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp), tint = Palette.Accent)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.news_title), style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary)
                Spacer(Modifier.weight(1f))
                if (item != null) Text(item.source, style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = item,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "headline",
                modifier = Modifier.weight(1f),
            ) { current ->
                if (current == null) {
                    Text(
                        stringResource(if (loading) R.string.news_loading else R.string.news_empty),
                        style = Type.body,
                        color = Palette.TextSecondary,
                    )
                } else {
                    Row {
                        Text(
                            current.title,
                            style = Type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (current.image != null && wide) {
                            Spacer(Modifier.width(12.dp))
                            AsyncImage(
                                model = current.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(newsTime(context, item?.time, now, locale), style = Type.tiny, color = Palette.TextTertiary)
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
        }
    }
}
