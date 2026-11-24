package io.github.remdee13.pavlovtv.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import io.github.remdee13.pavlovtv.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun formatTemp(value: Double): String = "${value.roundToInt()}°"

@StringRes
fun conditionRes(code: Int): Int = when (code) {
    0 -> R.string.weather_clear
    1 -> R.string.weather_mostly_clear
    2 -> R.string.weather_partly_cloudy
    3 -> R.string.weather_overcast
    45, 48 -> R.string.weather_fog
    51, 53, 55 -> R.string.weather_drizzle
    56, 57, 66, 67 -> R.string.weather_sleet
    61, 63 -> R.string.weather_rain
    65 -> R.string.weather_heavy_rain
    80, 81, 82 -> R.string.weather_showers
    71, 73, 75, 77, 85, 86 -> R.string.weather_snow
    95 -> R.string.weather_thunder
    96, 99 -> R.string.weather_thunder_hail
    else -> R.string.weather_overcast
}

fun zoneOrDefault(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())

fun timeText(time: ZonedDateTime, use24h: Boolean, locale: Locale): String =
    time.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm", locale))

fun amPm(time: ZonedDateTime, locale: Locale): String = time.format(DateTimeFormatter.ofPattern("a", locale))

fun longDate(time: ZonedDateTime, locale: Locale): String {
    val day = time.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    val date = time.format(DateTimeFormatter.ofPattern(if (locale.language == "en") "MMMM d" else "d MMMM", locale))
    return "$day, $date"
}

fun shortDay(isoDate: String, locale: Locale): String = runCatching {
    LocalDate.parse(isoDate).dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }
}.getOrDefault("")

/** "+3 h", "−1 day" style hint that tells how a world clock relates to local time. */
fun offsetHint(context: Context, zone: ZoneId, now: Instant): String {
    val local = ZonedDateTime.ofInstant(now, ZoneId.systemDefault())
    val there = ZonedDateTime.ofInstant(now, zone)
    val diffMinutes = (there.offset.totalSeconds - local.offset.totalSeconds) / 60
    val dayDiff = there.toLocalDate().compareTo(local.toLocalDate())
    val hours = diffMinutes / 60
    val minutes = kotlin.math.abs(diffMinutes % 60)
    val hourText = when {
        diffMinutes == 0 -> context.getString(R.string.clock_same_time)
        minutes == 0 -> context.getString(R.string.clock_offset_hours, if (hours > 0) "+$hours" else "−${-hours}")
        else -> context.getString(R.string.clock_offset_hours, (if (diffMinutes > 0) "+" else "−") + "${kotlin.math.abs(hours)}:${"%02d".format(minutes)}")
    }
    return when {
        dayDiff > 0 -> "$hourText · ${context.getString(R.string.clock_tomorrow)}"
        dayDiff < 0 -> "$hourText · ${context.getString(R.string.clock_yesterday)}"
        else -> hourText
    }
}

fun newsTime(context: Context, time: Long?, now: Long, locale: Locale): String {
    time ?: return ""
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> context.getString(R.string.news_just_now)
        minutes < 60 -> context.getString(R.string.news_minutes_ago, minutes.toInt())
        minutes < 12 * 60 -> context.getString(R.string.news_hours_ago, (minutes / 60).toInt())
        else -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))
    }
}
