package io.github.remdee13.pavlovtv.ui.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.ClockStyle
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.Moon
import io.github.remdee13.pavlovtv.data.Place
import io.github.remdee13.pavlovtv.data.Sun
import io.github.remdee13.pavlovtv.data.TempUnit
import io.github.remdee13.pavlovtv.data.WeatherBundle
import io.github.remdee13.pavlovtv.data.WeatherReport
import io.github.remdee13.pavlovtv.data.cityNameForZone
import io.github.remdee13.pavlovtv.ui.amPm
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.conditionRes
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.formatTemp
import io.github.remdee13.pavlovtv.ui.offsetHint
import io.github.remdee13.pavlovtv.ui.shortDay
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.timeText
import io.github.remdee13.pavlovtv.ui.zoneOrDefault
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A city on the home widget and the detail screens: its clock and, once fetched, its weather. */
class City(val place: Place, val style: ClockStyle, val weather: WeatherReport?) {
    val zone: ZoneId get() = zoneOrDefault(place.timezone)
}

/**
 * The main city first, then the others in the user's order. Until the main city is located,
 * it is the TV's own time zone, so the clock is right from the first frame.
 */
fun cityList(s: LauncherSettings, bundle: WeatherBundle?): List<City> {
    val reports = listOfNotNull(bundle?.main) + bundle?.extra.orEmpty()
    fun weatherFor(p: Place) = reports.firstOrNull { p.hasCoordinates && it.place.lat == p.lat && it.place.lon == p.lon }
    val auto = s.weatherAuto || s.weatherPlace == null
    val mainReport = if (auto) bundle?.main else s.weatherPlace?.let(::weatherFor)
    val mainPlace = (if (auto) bundle?.main?.place else s.weatherPlace)
        ?.let { p -> if (p.timezone.isEmpty()) mainReport?.place ?: p else p }
        ?: ZoneId.systemDefault().id.let { Place(name = cityNameForZone(it), timezone = it) }
    val main = City(mainPlace, s.clockStyles[LauncherSettings.MAIN_CITY] ?: ClockStyle.DIGITAL, mainReport)
    return listOf(main) + s.extraPlaces.map { p ->
        val report = weatherFor(p)
        City(if (p.timezone.isEmpty()) report?.place ?: p else p, s.clockStyles[p.key] ?: ClockStyle.ANALOG, report)
    }
}

/** One width whatever the number of cities, so the row never jumps. */
val CitiesWidgetWidth = 376.dp

private val SunColor = Color(0xFFFFC94D)

/**
 * The main city's weather on its sky as it is right now: night blue, sunset orange or daylight.
 * Next to it the sun on its arc by day, the moon by night. Up to three more cities sit along the
 * bottom, or the next hours when there is only one city.
 */
@Composable
fun CitiesWidget(cities: List<City>, use24h: Boolean, unit: TempUnit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now by rememberNow(1000L)
    val locale = currentLocale()
    val main = cities.firstOrNull() ?: return
    val others = cities.drop(1).take(LauncherSettings.HOME_CITIES - 1)
    val instant = Instant.ofEpochMilli(now)
    val time = ZonedDateTime.ofInstant(instant, main.zone)
    // The sun moves slowly: a new sky once a minute is plenty.
    val sky = remember(main.place.key, now / 60_000) { Sky.of(main.place, now, time.hour) }
    val ink = sky.ink
    val soft = ink.copy(alpha = 0.72f)
    val line = ink.copy(alpha = 0.18f)
    // The top bar already shows the TV's clock; the widget repeats the time only for another zone.
    val sameZone = main.zone.rules.getOffset(instant) == ZoneId.systemDefault().rules.getOffset(instant)
    GlassCard(onClick = onClick, modifier = modifier.height(WidgetHeight), shape = WidgetShape, rimOnTop = true) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(sky.top, sky.bottom)), WidgetShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(12.dp), tint = soft)
                Spacer(Modifier.width(3.dp))
                Text(
                    main.place.name + " · " + time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)),
                    style = Type.tiny, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!sameZone) {
                    Text(
                        " · " + timeText(time, use24h, locale) + if (use24h) "" else " " + amPm(time, locale),
                        style = Type.tiny.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, softWrap = false,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                MainWeather(main.weather, now, unit, ink, soft, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(10.dp))
                SkyNow(main.place, main.zone, now, use24h, locale, ink, soft, Modifier.width(104.dp).fillMaxHeight())
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(line))
            Spacer(Modifier.height(7.dp))
            if (others.isEmpty()) {
                HourStrip(main.weather, now, use24h, locale, ink, soft)
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    others.forEachIndexed { i, city ->
                        if (i > 0) Box(Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight().background(line))
                        FooterCity(city, now, use24h, locale, ink, soft, Modifier.weight(1f))
                    }
                    // Fewer than three: the columns keep their width instead of stretching.
                    repeat(LauncherSettings.HOME_CITIES - 1 - others.size) { Spacer(Modifier.padding(horizontal = 8.dp).width(1.dp).weight(1f)) }
                }
            }
        }
    }
}

/** Temperature and sky in large, then what it feels like, humidity, wind and the chance of rain. */
@Composable
private fun MainWeather(report: WeatherReport?, now: Long, unit: TempUnit, ink: Color, soft: Color, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        if (report == null) {
            Text(stringResource(R.string.weather_loading), style = Type.small, color = soft)
            return@Column
        }
        val w = report.now
        val today = report.days.firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(w.code, w.isDay, Modifier.size(38.dp))
            Spacer(Modifier.width(6.dp))
            Text(formatTemp(w.temp), style = Type.bigNumber.copy(fontSize = 38.sp, shadow = null), color = ink, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(conditionRes(w.code)), style = Type.small.copy(fontWeight = FontWeight.Medium), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.weather_feels_like, formatTemp(w.feelsLike)), style = Type.tiny, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                today?.let { Text("↑${formatTemp(it.max)} ↓${formatTemp(it.min)}", style = Type.tiny, color = soft, maxLines = 1) }
            }
        }
        // Rain over the next few hours says more in the evening than the whole day's chance.
        val rain = upcomingHours(report, now).take(6).maxOfOrNull { it.rainChance } ?: today?.rainChance
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Detail(Icons.Outlined.WaterDrop, "${w.humidity}%", ink, soft)
            Detail(Icons.Outlined.Air, windText(context, w.wind, unit), ink, soft)
            rain?.let { Detail(Icons.Outlined.Umbrella, "$it%", ink, soft) }
        }
    }
}

@Composable
private fun Detail(icon: ImageVector, text: String, ink: Color, soft: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = soft)
        Spacer(Modifier.width(3.dp))
        Text(text, style = Type.tiny.copy(fontWeight = FontWeight.Medium), color = ink, maxLines = 1, softWrap = false)
    }
}

/** The sun on its way from sunrise to sunset while it is up, the moon's phase once it is down. */
@Composable
private fun SkyNow(place: Place, zone: ZoneId, now: Long, use24h: Boolean, locale: Locale, ink: Color, soft: Color, modifier: Modifier) {
    val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val day = remember(place.key, date) { if (place.hasCoordinates) Sun.day(place.lat, place.lon, date, zone) else null }
    val rise = day?.sunrise
    val set = day?.sunset
    val up = day != null && (day.alwaysUp || (rise != null && set != null && now in rise..set))
    if (!up) {
        MoonNow(place.hasCoordinates && place.lat < 0, now, ink, soft, modifier)
        return
    }
    val fraction = if (rise != null && set != null && set > rise) ((now - rise).toFloat() / (set - rise)).coerceIn(0f, 1f) else 0.5f
    Column(modifier) {
        SunArc(fraction, ink, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(2.dp))
        if (rise != null && set != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(rise, zone, use24h, locale), style = Type.tiny, color = soft, maxLines = 1, softWrap = false)
                Text(clock(set, zone, use24h, locale), style = Type.tiny, color = soft, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Half an ellipse over the horizon: dashed ahead of the sun, solid behind it. */
@Composable
private fun SunArc(fraction: Float, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val side = 6.dp.toPx()
        val top = 9.dp.toPx()
        val horizon = size.height - 1.dp.toPx()
        val rx = (size.width - 2 * side) / 2
        val ry = (horizon - top).coerceAtLeast(1f)
        val cx = size.width / 2
        fun at(f: Float) = Offset(cx - rx * cos(PI * f).toFloat(), horizon - ry * sin(PI * f).toFloat())
        fun arc(from: Float, to: Float) = Path().apply {
            val steps = 40
            for (i in 0..steps) {
                val p = at(from + (to - from) * i / steps)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        val stroke = 1.5.dp.toPx()
        drawLine(ink.copy(alpha = 0.25f), Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
        drawPath(arc(fraction, 1f), ink.copy(alpha = 0.35f), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
        drawPath(arc(0f, fraction), SunColor.copy(alpha = 0.9f), style = Stroke(stroke * 1.4f, cap = StrokeCap.Round))
        val sun = at(fraction)
        drawCircle(SunColor.copy(alpha = 0.28f), 9.dp.toPx(), sun)
        drawCircle(SunColor, 5.dp.toPx(), sun)
    }
}

@Composable
private fun MoonNow(southern: Boolean, now: Long, ink: Color, soft: Color, modifier: Modifier) {
    val names = stringArrayResource(R.array.moon_phases)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        MoonDisc(Moon.phase(now), southern, Modifier.size(34.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            names.getOrElse(Moon.phaseIndex(now)) { "" },
            style = Type.tiny.copy(fontWeight = FontWeight.Medium), color = ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text(stringResource(R.string.moon_lit, (Moon.illumination(now) * 100).roundToInt()), style = Type.tiny, color = soft, maxLines = 1)
    }
}

@Composable
private fun FooterCity(city: City, now: Long, use24h: Boolean, locale: Locale, ink: Color, soft: Color, modifier: Modifier) {
    val context = LocalContext.current
    val instant = Instant.ofEpochMilli(now)
    val time = ZonedDateTime.ofInstant(instant, city.zone)
    Column(modifier) {
        Text(city.place.name, style = Type.tiny.copy(fontWeight = FontWeight.Medium), color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (city.style == ClockStyle.ANALOG) {
                val sky = remember(city.place.key, now / 60_000) { Sky.of(city.place, now, time.hour) }
                SkyDial(time, sky, Modifier.size(20.dp), showSeconds = false)
                Spacer(Modifier.width(5.dp))
            }
            Text(timeText(time, use24h, locale), style = Type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = ink, maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            city.weather?.let { weather ->
                WeatherIcon(weather.now.code, weather.now.isDay, Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text(formatTemp(weather.now.temp), style = Type.tiny, color = ink, maxLines = 1)
                Spacer(Modifier.width(5.dp))
            }
            Text(offsetHint(context, city.zone, instant).substringBefore(" · "), style = Type.tiny, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The main city's next hours in one line; the coming days when there is no hourly forecast. */
@Composable
private fun HourStrip(report: WeatherReport?, now: Long, use24h: Boolean, locale: Locale, ink: Color, soft: Color) {
    if (report == null) {
        Text(stringResource(R.string.weather_loading), style = Type.tiny, color = soft)
        return
    }
    val hours = upcomingHours(report, now).take(5)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (hours.size >= 3) {
            hours.forEachIndexed { i, hour ->
                StripCell(
                    if (i == 0) stringResource(R.string.weather_now) else hourText(hour.time, use24h, locale),
                    hour.code, hour.isDay,
                    formatTemp(if (i == 0) report.now.temp else hour.temp),
                    ink, soft,
                )
            }
        } else {
            report.days.drop(1).take(5).forEach { day ->
                StripCell(shortDay(day.date, locale), day.code, true, "${formatTemp(day.max)} ${formatTemp(day.min)}", ink, soft)
            }
        }
    }
}

@Composable
private fun StripCell(label: String, code: Int, isDay: Boolean, temp: String, ink: Color, soft: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.tiny, color = soft, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(code, isDay, Modifier.size(18.dp))
            Spacer(Modifier.width(3.dp))
            Text(temp, style = Type.small.copy(fontWeight = FontWeight.Medium), color = ink, maxLines = 1)
        }
    }
}
