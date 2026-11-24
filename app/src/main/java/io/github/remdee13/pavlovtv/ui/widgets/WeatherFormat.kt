package io.github.remdee13.pavlovtv.ui.widgets

import android.content.Context
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.TempUnit
import io.github.remdee13.pavlovtv.data.WeatherHour
import io.github.remdee13.pavlovtv.data.WeatherReport
import io.github.remdee13.pavlovtv.ui.zoneOrDefault
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

internal fun parseLocal(iso: String): LocalDateTime? = runCatching { LocalDateTime.parse(iso) }.getOrNull()

/** Forecast hours from the current one on, in the place's own time. */
internal fun upcomingHours(report: WeatherReport, now: Long): List<WeatherHour> {
    val current = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zoneOrDefault(report.place.timezone)).truncatedTo(ChronoUnit.HOURS)
    return report.hours.filter { hour -> parseLocal(hour.time)?.let { !it.isBefore(current) } == true }
}

/** "18:00" or "6 PM". */
internal fun hourText(iso: String, use24h: Boolean, locale: Locale): String {
    val time = parseLocal(iso) ?: return ""
    return if (use24h) "%02d:00".format(time.hour) else time.format(DateTimeFormatter.ofPattern("h a", locale))
}

/** "06:21" or "6:21 AM". */
internal fun clockText(iso: String, use24h: Boolean, locale: Locale): String {
    val time = parseLocal(iso) ?: return "—"
    return time.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale))
}

internal fun uvLevel(uv: Double): Int = when {
    uv < 3 -> R.string.uv_low
    uv < 6 -> R.string.uv_moderate
    uv < 8 -> R.string.uv_high
    uv < 11 -> R.string.uv_very_high
    else -> R.string.uv_extreme
}

/** Compass point the wind comes from, in the user's language. */
internal fun compass(context: Context, degrees: Int): String {
    val points = context.resources.getStringArray(R.array.compass_points)
    return points[(((degrees % 360) + 360) % 360 + 22) / 45 % 8]
}

/** Pressure the way people read it: mmHg in Russian, hPa elsewhere. */
internal fun pressureText(context: Context, hpa: Double, locale: Locale): String =
    if (locale.language == "ru") {
        "${(hpa * 0.750062).roundToInt()} ${context.getString(R.string.unit_mmhg)}"
    } else {
        "${hpa.roundToInt()} ${context.getString(R.string.unit_hpa)}"
    }

internal fun visibilityText(context: Context, meters: Double, unit: TempUnit): String =
    if (unit == TempUnit.FAHRENHEIT) {
        // Open-Meteo sends feet with imperial units.
        "%.1f %s".format(meters / 5280.0, context.getString(R.string.unit_mi))
    } else {
        val km = meters / 1000.0
        (if (km >= 10) "${km.roundToInt()}" else "%.1f".format(km)) + " " + context.getString(R.string.unit_km)
    }

internal fun windText(context: Context, speed: Double, unit: TempUnit): String =
    "${speed.roundToInt()} " + context.getString(if (unit == TempUnit.FAHRENHEIT) R.string.unit_mph else R.string.unit_kmh)

internal fun rainText(context: Context, amount: Double, unit: TempUnit): String =
    if (unit == TempUnit.FAHRENHEIT) "%.2f %s".format(amount, context.getString(R.string.unit_in))
    else (if (amount >= 10) "${amount.roundToInt()}" else "%.1f".format(amount)) + " " + context.getString(R.string.unit_mm)
