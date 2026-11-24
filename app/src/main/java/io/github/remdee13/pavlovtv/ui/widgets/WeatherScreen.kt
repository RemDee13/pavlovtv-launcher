package io.github.remdee13.pavlovtv.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.TempUnit
import io.github.remdee13.pavlovtv.data.WeatherReport
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.GlassPanel
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.conditionRes
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.formatTemp
import io.github.remdee13.pavlovtv.ui.shortDay
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.timeText
import io.github.remdee13.pavlovtv.ui.zoneOrDefault
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Every city in detail: now, 8 measurements, the next 24 hours and the week. */
@Composable
fun WeatherScreen(vm: LauncherViewModel, startIndex: Int, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenTime: (Int) -> Unit) {
    val bundle by vm.weather.collectAsState()
    val settings by vm.settings.collectAsState()
    val cities = cityList(settings, bundle)
    val unit = bundle?.unit ?: TempUnit.CELSIUS
    var selected by remember { mutableIntStateOf(startIndex.coerceIn(0, cities.lastIndex)) }
    val first = remember { FocusRequester() }
    val now by rememberNow(60_000L)
    ScreenLayer(onBack = onBack, scrim = Color.Black.copy(alpha = 0.5f), initialFocus = first) {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weather_title), style = Type.clock.copy(fontSize = 28.sp))
                Spacer(Modifier.width(16.dp))
                // Up to ten cities: the tabs scroll sideways as focus moves along them.
                LazyRow(
                    Modifier.weight(1f),
                    state = rememberLazyListState(initialFirstVisibleItemIndex = selected),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(cities, key = { i, c -> "$i${c.place.key}" }) { i, city ->
                        CityTab(city, selected == i, if (i == selected) first else null) { selected = i }
                    }
                }
                Spacer(Modifier.width(12.dp))
                GlassButton(stringResource(R.string.action_refresh), { vm.refreshWeather() }, icon = Icons.Outlined.Refresh)
                Spacer(Modifier.width(10.dp))
                GlassButton(stringResource(R.string.action_time_screen), { onOpenTime(selected) }, icon = Icons.Outlined.Schedule)
                Spacer(Modifier.width(10.dp))
                GlassButton(stringResource(R.string.weather_cities), onOpenSettings, icon = Icons.Outlined.Tune)
            }
            Spacer(Modifier.height(14.dp))
            val report = cities.getOrNull(selected.coerceIn(0, cities.lastIndex))?.weather
            if (report == null) {
                Text(stringResource(if (bundle == null) R.string.weather_loading else R.string.weather_unavailable), style = Type.body, color = Palette.TextSecondary)
                return@Column
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassPanel(Modifier.fillMaxWidth(), strength = 0.6f) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Hero(report, now, settings.use24h)
                            Spacer(Modifier.height(14.dp))
                            Details(report, unit, settings.use24h)
                        }
                    }
                    GlassPanel(Modifier.fillMaxWidth().weight(1f), strength = 0.6f) {
                        Hourly(report, now, settings.use24h)
                    }
                }
                GlassPanel(Modifier.width(300.dp).fillMaxHeight(), strength = 0.6f) {
                    Week(report)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.weather_attribution), style = Type.tiny, color = Palette.TextTertiary)
        }
    }
}

@Composable
private fun CityTab(city: City, selected: Boolean, focusRequester: FocusRequester?, onFocus: () -> Unit) {
    GlassCard(
        onClick = onFocus,
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        onFocus = { if (it) onFocus() },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            city.weather?.let { report ->
                WeatherIcon(report.now.code, report.now.isDay, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                city.place.name,
                style = Type.small.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
                maxLines = 1,
            )
            city.weather?.let { report ->
                Spacer(Modifier.width(8.dp))
                Text(formatTemp(report.now.temp), style = Type.small, color = Palette.TextSecondary)
            }
        }
    }
}

@Composable
private fun Hero(report: WeatherReport, now: Long, use24h: Boolean) {
    val locale = currentLocale()
    val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneOrDefault(report.place.timezone))
    val today = report.days.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(report.now.code, report.now.isDay, Modifier.size(84.dp))
        Spacer(Modifier.width(14.dp))
        Text(formatTemp(report.now.temp), style = Type.bigNumber.copy(fontSize = 62.sp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(conditionRes(report.now.code)), style = Type.title.copy(fontSize = 20.sp))
            Text(stringResource(R.string.weather_feels_like, formatTemp(report.now.feelsLike)), style = Type.body, color = Palette.TextSecondary)
            if (today != null) Text("↑${formatTemp(today.max)}  ↓${formatTemp(today.min)}", style = Type.body, color = Palette.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(report.place.name, style = Type.title, maxLines = 1)
            if (report.place.country.isNotEmpty()) {
                Text(report.place.country, style = Type.small, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp), textAlign = TextAlign.End)
            }
            Text(stringResource(R.string.weather_local_time, timeText(local, use24h, locale)), style = Type.small, color = Palette.Accent)
        }
    }
}

@Composable
private fun Details(report: WeatherReport, unit: TempUnit, use24h: Boolean) {
    val context = LocalContext.current
    val locale = currentLocale()
    val now = report.now
    val today = report.days.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.weather_humidity_title), "${now.humidity}%", stringResource(R.string.weather_dew_point, formatTemp(now.dewPoint)))
            Tile(stringResource(R.string.weather_wind_title), windText(context, now.wind, unit), stringResource(R.string.weather_gusts, windText(context, now.gusts, unit))) {
                // Arrow shows where the wind blows to; the letters say where it comes from.
                Icon(Icons.Outlined.Navigation, null, Modifier.size(14.dp).rotate(now.windDir + 180f), tint = Palette.Accent)
                Spacer(Modifier.width(3.dp))
                Text(compass(context, now.windDir), style = Type.tiny, color = Palette.Accent)
            }
            Tile(stringResource(R.string.weather_uv_title), "${now.uv.roundToInt()}", stringResource(uvLevel(now.uv)) + (today?.let { " · max ${it.uvMax.roundToInt()}" } ?: ""))
            Tile(stringResource(R.string.weather_pressure_title), pressureText(context, now.pressure, locale), null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.weather_visibility_title), visibilityText(context, now.visibility, unit), null)
            Tile(stringResource(R.string.weather_clouds_title), "${now.cloudCover}%", null)
            Tile(
                stringResource(R.string.weather_rain_title),
                today?.let { rainText(context, it.rainSum, unit) } ?: "—",
                today?.let { stringResource(R.string.weather_rain_chance, it.rainChance) },
            )
            Tile(
                stringResource(R.string.weather_sunrise_title),
                today?.let { clockText(it.sunrise, use24h, locale) } ?: "—",
                today?.let { stringResource(R.string.weather_sunset, clockText(it.sunset, use24h, locale)) },
            )
        }
    }
}

@Composable
private fun RowScope.Tile(label: String, value: String, sub: String?, badge: (@Composable RowScope.() -> Unit)? = null) {
    Column(
        Modifier
            .weight(1f)
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            badge?.invoke(this)
        }
        Text(value, style = Type.title.copy(fontSize = 18.sp), maxLines = 1)
        Text(sub ?: " ", style = Type.tiny, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 24 hours, every second hour: time, icon, a temperature curve, degrees and rain chance. */
@Composable
private fun Hourly(report: WeatherReport, now: Long, use24h: Boolean) {
    val locale = currentLocale()
    val hours = upcomingHours(report, now).take(24).filterIndexed { i, _ -> i % 2 == 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.weather_next_hours), style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary)
        if (hours.size < 2) return@Column
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val column = maxWidth / hours.size
            val curveTop = 46.dp
            val curveHeight = 26.dp
            val temps = hours.map { it.temp }
            val lo = temps.min()
            val hi = temps.max()
            Canvas(Modifier.fillMaxSize()) {
                val step = size.width / hours.size
                val points = temps.mapIndexed { i, t ->
                    val k = if (hi > lo) ((t - lo) / (hi - lo)).toFloat() else 0.5f
                    Offset(step * (i + 0.5f), curveTop.toPx() + curveHeight.toPx() * (1f - k))
                }
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        val a = points[i - 1]
                        val b = points[i]
                        val mid = (a.x + b.x) / 2f
                        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                    }
                }
                drawPath(path, Brush.horizontalGradient(listOf(Palette.Accent, Palette.Warm)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                points.forEach { drawCircle(Color.White, 2.5.dp.toPx(), it) }
            }
            Row(Modifier.fillMaxSize()) {
                hours.forEachIndexed { i, hour ->
                    Column(Modifier.width(column), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) stringResource(R.string.weather_now) else hourText(hour.time, use24h, locale), style = Type.tiny, color = Palette.TextSecondary, maxLines = 1)
                        WeatherIcon(hour.code, hour.isDay, Modifier.size(24.dp))
                        Spacer(Modifier.height(curveTop + curveHeight - 13.dp - 24.dp + 6.dp))
                        Text(formatTemp(hour.temp), style = Type.small.copy(fontWeight = FontWeight.Medium))
                        Text(
                            if (hour.rainChance >= 10) "${hour.rainChance}%" else " ",
                            style = Type.tiny,
                            color = if (hour.rainChance >= 40) Palette.Accent else Palette.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** The week: rain chance and each day's range on one shared scale. */
@Composable
private fun Week(report: WeatherReport) {
    val locale = currentLocale()
    val days = report.days.take(7)
    val lo = days.minOfOrNull { it.min } ?: 0.0
    val hi = days.maxOfOrNull { it.max } ?: 1.0
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.weather_week), style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary)
        Spacer(Modifier.height(6.dp))
        days.forEachIndexed { i, day ->
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i == 0) stringResource(R.string.weather_today) else shortDay(day.date, locale),
                    style = Type.small.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = Modifier.width(58.dp),
                )
                WeatherIcon(day.code, true, Modifier.size(24.dp))
                Text(
                    if (day.rainChance >= 10) "${day.rainChance}%" else "",
                    style = Type.tiny,
                    color = if (day.rainChance >= 40) Palette.Accent else Palette.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp),
                )
                Text(formatTemp(day.min), style = Type.small, color = Palette.TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
                RangeBar(day.min, day.max, lo, hi, Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(formatTemp(day.max), style = Type.small.copy(fontWeight = FontWeight.Medium), modifier = Modifier.width(30.dp))
            }
        }
    }
}

@Composable
private fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, modifier: Modifier, height: Dp = 6.dp) {
    Box(modifier.height(height).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(height))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            val start = maxWidth * ((min - lo) / span).toFloat()
            val width = (maxWidth * ((max - min) / span).toFloat()).coerceAtLeast(height)
            Box(
                Modifier
                    .offset(x = start)
                    .width(width)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Palette.Accent, Palette.Warm)), RoundedCornerShape(height)),
            )
        }
    }
}
