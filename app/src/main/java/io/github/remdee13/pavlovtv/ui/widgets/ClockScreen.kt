package io.github.remdee13.pavlovtv.ui.widgets

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AirReport
import io.github.remdee13.pavlovtv.data.CityInfo
import io.github.remdee13.pavlovtv.data.LocalInfoRepository
import io.github.remdee13.pavlovtv.data.Moon
import io.github.remdee13.pavlovtv.data.Place
import io.github.remdee13.pavlovtv.data.SeaReport
import io.github.remdee13.pavlovtv.data.Sun
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
import io.github.remdee13.pavlovtv.ui.longDate
import io.github.remdee13.pavlovtv.ui.offsetHint
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.zoneOrDefault
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val PanelPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private val Night = Color(0xFF7B8CFF)
private val SunGold = Color(0xFFFFD27A)

/** Where the clock city is, once known: a clock may only have a time zone and needs a lookup. */
private class Located(val done: Boolean, val place: Place?)

/**
 * Every clock city in detail: the time, the sun's day, the moon, the tides for cities by the
 * sea, and a few local facts (air, money, the next public holiday, daylight saving).
 */
@Composable
fun ClockScreen(vm: LauncherViewModel, startIndex: Int, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenWeather: (Int) -> Unit) {
    val settings by vm.settings.collectAsState()
    val bundle by vm.weather.collectAsState()
    val cities = cityList(settings, bundle)
    var selected by remember { mutableIntStateOf(startIndex.coerceIn(0, cities.lastIndex)) }
    val first = remember { FocusRequester() }
    val now by rememberNow(60_000L)
    ScreenLayer(onBack = onBack, scrim = Color.Black.copy(alpha = 0.5f), initialFocus = first) {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_clocks), style = Type.clock.copy(fontSize = 28.sp))
                Spacer(Modifier.width(16.dp))
                LazyRow(
                    Modifier.weight(1f),
                    state = rememberLazyListState(initialFirstVisibleItemIndex = selected),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(cities, key = { i, c -> "$i${c.place.key}" }) { i, city ->
                        ClockTab(city.place, now, settings.use24h, selected == i, if (i == selected) first else null) { selected = i }
                    }
                }
                Spacer(Modifier.width(12.dp))
                GlassButton(stringResource(R.string.settings_weather), { onOpenWeather(selected) }, icon = Icons.Outlined.Cloud)
                Spacer(Modifier.width(10.dp))
                GlassButton(stringResource(R.string.weather_cities), onOpenSettings, icon = Icons.Outlined.Tune)
            }
            Spacer(Modifier.height(12.dp))
            val city = cities.getOrNull(selected.coerceIn(0, cities.lastIndex)) ?: return@Column
            CityDetails(vm, city.place, city.weather, now, settings.use24h, settings.tempUnit == TempUnit.FAHRENHEIT, Modifier.weight(1f))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.clock_info_attribution), style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ClockTab(place: Place, now: Long, use24h: Boolean, selected: Boolean, focusRequester: FocusRequester?, onFocus: () -> Unit) {
    val locale = currentLocale()
    val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneOrDefault(place.timezone))
    val sky = remember(place.key, now / 60_000) { Sky.of(place, now, time.hour) }
    GlassCard(
        onClick = onFocus,
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        onFocus = { if (it) onFocus() },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).background(Brush.verticalGradient(listOf(sky.top, sky.bottom)), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                place.name,
                style = Type.small.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(clock(now, time.zone, use24h, locale), style = Type.small, color = Palette.TextSecondary)
        }
    }
}

@Composable
private fun CityDetails(
    vm: LauncherViewModel,
    clockPlace: Place,
    report: WeatherReport?,
    now: Long,
    use24h: Boolean,
    imperial: Boolean,
    modifier: Modifier,
) {
    val zone = zoneOrDefault(clockPlace.timezone)
    val located by produceState(Located(clockPlace.hasCoordinates, clockPlace.takeIf { it.hasCoordinates }), clockPlace.key) {
        value = Located(clockPlace.hasCoordinates, clockPlace.takeIf { it.hasCoordinates })
        if (!value.done) value = Located(true, vm.locatePlace(clockPlace))
    }
    val place = located.place
    val info by produceState<CityInfo?>(null, place?.key) {
        value = null
        val p = place ?: return@produceState
        delay(300) // focus may only be passing through the tabs
        value = vm.cityInfo(p)
    }
    val rates by produceState<Map<String, Double>?>(null) { value = vm.currencyRates() }
    val sea = info?.sea
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPanel(Modifier.fillMaxWidth(), strength = 0.6f) { Hero(clockPlace, place, zone, report, use24h) }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPanel(Modifier.weight(1f).fillMaxHeight(), strength = 0.6f) { SunPanel(place, located.done, zone, now, use24h, wide = sea == null) }
                if (sea != null) {
                    GlassPanel(Modifier.weight(1f).fillMaxHeight(), strength = 0.6f) { SeaPanel(sea, zone, now, use24h, imperial) }
                }
            }
        }
        Column(Modifier.width(290.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPanel(Modifier.fillMaxWidth(), strength = 0.6f) { MoonPanel((place?.lat ?: 0.0) < 0, zone, now) }
            GlassPanel(Modifier.fillMaxWidth().weight(1f), strength = 0.6f) {
                InfoPanel(clockPlace, zone, now, info, loading = place != null && info == null || !located.done, rates)
            }
        }
    }
}

@Composable
private fun Hero(clockPlace: Place, place: Place?, zone: ZoneId, report: WeatherReport?, use24h: Boolean) {
    val now by rememberNow(1000L)
    val locale = currentLocale()
    val context = LocalContext.current
    val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
    val skyPlace = place ?: clockPlace
    val sky = remember(skyPlace.key, now / 60_000) { Sky.of(skyPlace, now, time.hour) }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SkyDial(time, sky, Modifier.size(96.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm", locale)), style = Type.bigNumber.copy(fontSize = 54.sp))
                Text(
                    time.format(DateTimeFormatter.ofPattern(":ss", locale)) + if (use24h) "" else " " + time.format(DateTimeFormatter.ofPattern("a", locale)),
                    style = Type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Light),
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
                )
            }
            Text(longDate(time, locale), style = Type.body, color = Palette.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(clockPlace.name, style = Type.title.copy(fontSize = 22.sp), maxLines = 1)
            val country = clockPlace.country.ifEmpty { place?.country.orEmpty() }
            if (country.isNotEmpty()) {
                Text(country, style = Type.small, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(220.dp), textAlign = TextAlign.End)
            }
            Text(offsetHint(context, zone, Instant.ofEpochMilli(now)), style = Type.small, color = Palette.Accent)
            if (report != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(report.now.code, report.now.isDay, Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${formatTemp(report.now.temp)} · ${stringResource(conditionRes(report.now.code))}", style = Type.small, color = Palette.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Type.small, color = Palette.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun RowScope.InfoTile(label: String, value: String, sub: String?) {
    Column(
        Modifier
            .weight(1f)
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Type.title.copy(fontSize = 17.sp), maxLines = 1)
        if (sub != null) Text(sub, style = Type.tiny, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SunPanel(place: Place?, located: Boolean, zone: ZoneId, now: Long, use24h: Boolean, wide: Boolean) {
    val context = LocalContext.current
    val locale = currentLocale()
    val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val day = remember(place?.key, today) { place?.let { Sun.day(it.lat, it.lon, today, zone) } }
    Column(Modifier.fillMaxSize().padding(PanelPadding)) {
        PanelTitle(stringResource(R.string.sun_title), day?.lengthMinutes?.takeIf { !wide }?.let { duration(context, it) })
        if (place == null || day == null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(if (located) R.string.clock_no_location else R.string.weather_loading), style = Type.body, color = Palette.TextTertiary)
            return@Column
        }
        Spacer(Modifier.height(6.dp))
        DayCurve(place, zone, today, now, use24h, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(8.dp))
        if (day.alwaysUp || day.alwaysDown) {
            Text(stringResource(if (day.alwaysUp) R.string.sun_polar_day else R.string.sun_polar_night), style = Type.body, color = Palette.Warm)
            return@Column
        }
        fun at(ms: Long?) = ms?.let { clock(it, zone, use24h, locale) } ?: "—"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(stringResource(R.string.weather_sunrise_title), at(day.sunrise), null)
            InfoTile(stringResource(R.string.sun_sunset), at(day.sunset), null)
            if (wide) InfoTile(stringResource(R.string.sun_day_length), day.lengthMinutes?.let { duration(context, it) } ?: "—", null)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(stringResource(R.string.sun_dawn), at(day.dawn), null)
            InfoTile(stringResource(R.string.sun_dusk), at(day.dusk), null)
            if (wide) InfoTile(stringResource(R.string.sun_golden_hour), at(day.goldenEveningStart), null)
        }
    }
}

/** The sun's height over the local day: warm above the horizon, blue below, a dot for now. */
@Composable
private fun DayCurve(place: Place, zone: ZoneId, date: LocalDate, now: Long, use24h: Boolean, modifier: Modifier) {
    val locale = currentLocale()
    val start = remember(date, zone) { date.atStartOfDay(zone).toInstant().toEpochMilli() }
    val end = remember(date, zone) { date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val samples = remember(place.key, date) { (0..SAMPLES).map { Sun.elevation(place.lat, place.lon, start + (end - start) * it / SAMPLES) } }
    val sunNow = Sun.elevation(place.lat, place.lon, now)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val hi = max(samples.max(), 10.0) + 4
            val lo = min(samples.min(), -10.0) - 4
            fun y(e: Double) = (size.height * (hi - e) / (hi - lo)).toFloat()
            fun x(i: Int) = size.width * i / SAMPLES
            val horizon = y(0.0)
            fun curve(closeAt: Float?): Path = Path().apply {
                moveTo(0f, y(samples[0]))
                for (i in 1..SAMPLES) lineTo(x(i), y(samples[i]))
                if (closeAt != null) {
                    lineTo(size.width, closeAt)
                    lineTo(0f, closeAt)
                    close()
                }
            }
            clipRect(bottom = horizon) {
                drawPath(curve(size.height), Brush.verticalGradient(listOf(Palette.Warm.copy(alpha = 0.42f), Palette.Warm.copy(alpha = 0.06f)), startY = 0f, endY = horizon))
            }
            clipRect(top = horizon) {
                drawPath(curve(0f), Brush.verticalGradient(listOf(Night.copy(alpha = 0.05f), Night.copy(alpha = 0.22f)), startY = horizon, endY = size.height))
            }
            drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
            drawPath(curve(null), Brush.horizontalGradient(listOf(Night, Palette.Warm, Night)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val fx = ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f) * size.width
            val sy = y(sunNow)
            drawLine(Color.White.copy(alpha = 0.18f), Offset(fx, 0f), Offset(fx, size.height), 1.dp.toPx())
            val dot = if (sunNow > Sun.HORIZON) SunGold else Color(0xFFD5DCFF)
            drawCircle(dot.copy(alpha = 0.28f), 10.dp.toPx(), Offset(fx, sy))
            drawCircle(dot, 5.dp.toPx(), Offset(fx, sy))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 0).forEach { h ->
                Text(LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h a", locale)), style = Type.tiny, color = Palette.TextTertiary)
            }
        }
    }
}

private const val SAMPLES = 96

@Composable
private fun SeaPanel(sea: SeaReport, zone: ZoneId, now: Long, use24h: Boolean, imperial: Boolean) {
    val context = LocalContext.current
    val locale = currentLocale()
    Column(Modifier.fillMaxSize().padding(PanelPadding)) {
        PanelTitle(stringResource(R.string.sea_title), sea.waterTemp?.let { stringResource(R.string.sea_water) + " " + seaTemp(it, imperial) })
        Spacer(Modifier.height(6.dp))
        if (sea.hasTide) {
            TideCurve(sea, zone, now, use24h, imperial, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(8.dp))
            val high = sea.turns.firstOrNull { it.high && it.time > now }
            val low = sea.turns.firstOrNull { !it.high && it.time > now }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(stringResource(R.string.sea_high), high?.let { clock(it.time, zone, use24h, locale) } ?: "—", high?.let { length(context, it.height, imperial) })
                InfoTile(stringResource(R.string.sea_low), low?.let { clock(it.time, zone, use24h, locale) } ?: "—", low?.let { length(context, it.height, imperial) })
            }
        } else {
            Text(stringResource(R.string.sea_no_tide), style = Type.body, color = Palette.TextTertiary)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(stringResource(R.string.sea_water), sea.waterTemp?.let { seaTemp(it, imperial) } ?: "—", null)
            InfoTile(
                stringResource(R.string.sea_waves),
                sea.waveHeight?.let { length(context, it, imperial) } ?: "—",
                sea.wavePeriod?.let { stringResource(R.string.sea_period, it.roundToInt()) },
            )
        }
    }
}

/** Modelled sea level: a smooth line, highs and lows marked with their times. */
@Composable
private fun TideCurve(sea: SeaReport, zone: ZoneId, now: Long, use24h: Boolean, imperial: Boolean, modifier: Modifier) {
    val locale = currentLocale()
    val measurer = rememberTextMeasurer()
    val label = Type.tiny.copy(color = Palette.TextSecondary)
    Canvas(modifier) {
        val t0 = sea.times.first()
        val t1 = sea.times.last()
        val hi = sea.heights.max()
        val lo = sea.heights.min()
        val top = 16.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
        fun y(h: Double) = top + ((hi - h) / (hi - lo)).toFloat() * (bottom - top)
        val points = sea.times.indices.map { Offset(x(sea.times[it]), y(sea.heights[it])) }
        fun line(close: Boolean): Path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
            if (close) {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        }
        drawPath(line(true), Brush.verticalGradient(listOf(Palette.Accent.copy(alpha = 0.32f), Palette.Accent.copy(alpha = 0.02f)), startY = top, endY = size.height))
        drawPath(line(false), Palette.Accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        if (now in t0..t1) {
            val fx = x(now)
            val i = sea.times.indexOfLast { it <= now }.coerceIn(0, sea.times.size - 2)
            val k = (now - sea.times[i]).toFloat() / (sea.times[i + 1] - sea.times[i])
            val h = sea.heights[i] + (sea.heights[i + 1] - sea.heights[i]) * k
            drawLine(Color.White.copy(alpha = 0.25f), Offset(fx, 0f), Offset(fx, size.height), 1.dp.toPx())
            drawCircle(Color.White, 5.dp.toPx(), Offset(fx, y(h)))
        }
        sea.turns.filter { it.time in t0..t1 }.forEach { turn ->
            val p = Offset(x(turn.time), y(turn.height))
            drawCircle(if (turn.high) Palette.Accent else Color.White.copy(alpha = 0.7f), 3.5.dp.toPx(), p)
            val text = measurer.measure(clock(turn.time, zone, use24h, locale), label)
            val left = (p.x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            val ty = if (turn.high) p.y - text.size.height - 3.dp.toPx() else p.y + 3.dp.toPx()
            drawText(text, topLeft = Offset(left, ty.coerceIn(0f, size.height - text.size.height)))
        }
    }
}

@Composable
private fun MoonPanel(southern: Boolean, zone: ZoneId, now: Long) {
    val locale = currentLocale()
    val names = stringArrayResource(R.array.moon_phases)
    val phase = Moon.phase(now)
    Row(Modifier.fillMaxWidth().padding(PanelPadding), verticalAlignment = Alignment.CenterVertically) {
        MoonDisc(phase, southern, Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.moon_title), style = Type.tiny, color = Palette.TextTertiary)
            Text(names.getOrElse(Moon.phaseIndex(now)) { "" }, style = Type.title.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.moon_lit, (Moon.illumination(now) * 100).roundToInt()), style = Type.small, color = Palette.TextSecondary)
            Text(stringResource(R.string.moon_next_full, shortDate(Moon.next(now, 0.5), zone, locale)), style = Type.tiny, color = Palette.TextSecondary)
            Text(stringResource(R.string.moon_next_new, shortDate(Moon.next(now, 0.0), zone, locale)), style = Type.tiny, color = Palette.TextSecondary)
        }
    }
}

/** The moon as it looks tonight; mirrored for the southern hemisphere. */
@Composable
internal fun MoonDisc(phase: Double, southern: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = 0.05f), r * 1.18f, c)
        drawCircle(Color(0xFF2A3150), r, c)
        val k = cos(2 * PI * phase)
        val side = if ((phase < 0.5) != southern) 1.0 else -1.0
        val path = Path()
        val n = 48
        for (i in 0..n) {
            val a = -PI / 2 + PI * i / n
            val px = (c.x + side * r * cos(a)).toFloat()
            val py = (c.y + r * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in 0..n) {
            val a = PI / 2 - PI * i / n
            path.lineTo((c.x + side * r * k * cos(a)).toFloat(), (c.y + r * sin(a)).toFloat())
        }
        path.close()
        drawPath(path, Color(0xFFF3ECD8))
    }
}

@Composable
private fun InfoPanel(place: Place, zone: ZoneId, now: Long, info: CityInfo?, loading: Boolean, rates: Map<String, Double>?) {
    val context = LocalContext.current
    val locale = currentLocale()
    val instant = Instant.ofEpochMilli(now)
    Column(Modifier.fillMaxSize().padding(PanelPadding), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PanelTitle(stringResource(R.string.info_title))
        // Time zone and the next clock change.
        val zoneName = TimeZone.getTimeZone(zone.id).getDisplayName(zone.rules.isDaylightSavings(instant), TimeZone.LONG, locale)
        InfoLine(stringResource(R.string.timezone_title), "${utcText(zone, instant)} · $zoneName", dstText(context, zone, instant, locale))
        info?.air?.let { air -> AirLine(air) }
        currencyLine(context, place, rates, locale)?.let { (value, sub) -> InfoLine(stringResource(R.string.currency_title), value, sub) }
        info?.holiday?.let { holiday ->
            val today = ZonedDateTime.ofInstant(instant, zone).toLocalDate()
            val whenText = when (holiday.date) {
                today -> stringResource(R.string.holiday_today)
                today.plusDays(1) -> stringResource(R.string.holiday_tomorrow)
                else -> shortDate(holiday.date.atStartOfDay(zone).toInstant().toEpochMilli(), zone, locale)
            }
            InfoLine(stringResource(R.string.holiday_title), holiday.name, whenText)
        }
        if (loading) Text(stringResource(R.string.weather_loading), style = Type.small, color = Palette.TextTertiary)
    }
}

@Composable
private fun InfoLine(label: String, value: String, sub: String?, dot: Color? = null) {
    Column {
        Text(label, style = Type.tiny, color = Palette.TextTertiary, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(9.dp).background(dot, CircleShape))
                Spacer(Modifier.width(7.dp))
            }
            Text(value, style = Type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!sub.isNullOrEmpty()) Text(sub, style = Type.tiny, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AirLine(air: AirReport) {
    val levels = stringArrayResource(R.array.aqi_levels)
    val level = when {
        air.usAqi <= 50 -> 0
        air.usAqi <= 100 -> 1
        air.usAqi <= 150 -> 2
        air.usAqi <= 200 -> 3
        air.usAqi <= 300 -> 4
        else -> 5
    }
    val colors = listOf(Color(0xFF5BD18B), Color(0xFFF2C94C), Color(0xFFF2994A), Color(0xFFEB5757), Color(0xFFA66BD6), Color(0xFF9B3B4E))
    InfoLine(
        stringResource(R.string.air_title),
        "${air.usAqi} · ${levels.getOrElse(level) { "" }}",
        air.pm25?.let { "PM2.5 ${it.roundToInt()} µg/m³ · US AQI" },
        dot = colors[level],
    )
}

/** "1 RUB = 0.41 THB" against the currency at home, or against the dollar and euro when it is the same. */
private fun currencyLine(context: Context, place: Place, rates: Map<String, Double>?, locale: Locale): Pair<String, String>? {
    val local = LocalInfoRepository.currencyOf(place.timezone) ?: return null
    val home = LocalInfoRepository.currencyOf(LocalInfoRepository.homeZone())
    val name = local.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    val perUsd = rates ?: return local.currencyCode to name
    fun rate(code: String) = perUsd[code]
    val localRate = rate(local.currencyCode) ?: return local.currencyCode to name
    if (home != null && home.currencyCode != local.currencyCode) {
        val homeRate = rate(home.currencyCode) ?: return local.currencyCode to name
        return "1 ${local.currencyCode} = ${number(homeRate / localRate, locale)} ${home.currencyCode}" to name
    }
    val bases = listOf("USD", "EUR").filter { it != local.currencyCode }
    val parts = bases.mapNotNull { base -> rate(base)?.let { "1 $base = ${number(localRate / it, locale)} ${local.currencyCode}" } }
    return (parts.firstOrNull() ?: local.currencyCode) to (listOf(name) + parts.drop(1)).joinToString(" · ")
}

private fun number(value: Double, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = when {
        value >= 100 -> 0
        value >= 1 -> 2
        value >= 0.01 -> 3
        else -> 5
    }
    format.minimumFractionDigits = 0
    return format.format(value)
}

private fun utcText(zone: ZoneId, instant: Instant): String {
    val seconds = zone.rules.getOffset(instant).totalSeconds
    if (seconds == 0) return "UTC"
    val sign = if (seconds > 0) "+" else "−"
    val h = abs(seconds) / 3600
    val m = abs(seconds) % 3600 / 60
    return "UTC$sign$h" + if (m != 0) ":%02d".format(m) else ""
}

private fun dstText(context: Context, zone: ZoneId, instant: Instant, locale: Locale): String {
    val rules = zone.rules
    val next = if (rules.isFixedOffset) null else rules.nextTransition(instant)
    if (next == null || next.instant.isAfter(instant.plusSeconds(400L * 86_400))) return context.getString(R.string.dst_none)
    val delta = (next.offsetAfter.totalSeconds - next.offsetBefore.totalSeconds) / 60
    val amount = if (abs(delta) % 60 == 0) context.getString(R.string.clock_offset_hours, "${abs(delta) / 60}") else "${abs(delta)} ${context.getString(R.string.unit_min)}"
    val shift = (if (delta > 0) "+" else "−") + amount
    return context.getString(R.string.dst_next, shortDate(next.instant.toEpochMilli(), zone, locale), shift)
}

internal fun clock(ms: Long, zone: ZoneId, use24h: Boolean, locale: Locale): String =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale))

/** "Fri, 27 Nov" in the order the language uses. */
private fun shortDate(ms: Long, zone: ZoneId, locale: Locale): String =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale))

private fun duration(context: Context, minutes: Long): String = context.getString(R.string.duration_hm, minutes / 60, minutes % 60)

private fun seaTemp(celsius: Double, imperial: Boolean): String = formatTemp(if (imperial) celsius * 9 / 5 + 32 else celsius)

private fun length(context: Context, meters: Double, imperial: Boolean): String =
    if (imperial) "%.1f %s".format(meters * 3.281, context.getString(R.string.unit_ft)) else "%.1f %s".format(meters, context.getString(R.string.unit_m))
