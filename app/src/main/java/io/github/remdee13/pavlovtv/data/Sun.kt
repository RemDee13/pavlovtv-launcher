package io.github.remdee13.pavlovtv.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Where the sun is: enough precision (~0.5°) to tell day, sunset glow and night apart. */
object Sun {
    /** Height of the sun above the horizon in degrees; negative after sunset. */
    fun elevation(lat: Double, lon: Double, epochMillis: Long): Double {
        val n = epochMillis / 86_400_000.0 + 2440587.5 - 2451545.0
        val meanLongitude = (280.460 + 0.9856474 * n).mod(360.0)
        val anomaly = Math.toRadians((357.528 + 0.9856003 * n).mod(360.0))
        val eclipticLongitude = Math.toRadians(meanLongitude + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly))
        val obliquity = Math.toRadians(23.439 - 0.0000004 * n)
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val siderealHours = (18.697374558 + 24.06570982441908 * n).mod(24.0)
        val hourAngle = Math.toRadians(siderealHours * 15 + lon) - rightAscension
        val latitude = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle)))
    }

    /** Times the sun crosses the heights that matter for one local day, in epoch millis. */
    fun day(lat: Double, lon: Double, date: LocalDate, zone: ZoneId): SunDay {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val step = 10 * 60_000L
        val times = (start..end step step).toList()
        val heights = times.map { elevation(lat, lon, it) }
        fun crossing(level: Double, rising: Boolean): Long? {
            for (i in 1 until times.size) {
                val a = heights[i - 1] - level
                val b = heights[i] - level
                if (if (rising) a < 0 && b >= 0 else a >= 0 && b < 0) {
                    var lo = times[i - 1]
                    var hi = times[i]
                    repeat(12) {
                        val mid = (lo + hi) / 2
                        val below = elevation(lat, lon, mid) < level
                        if (below == rising) lo = mid else hi = mid
                    }
                    return (lo + hi) / 2
                }
            }
            return null
        }
        val top = heights.indices.maxBy { heights[it] }
        return SunDay(
            dawn = crossing(-6.0, true),
            sunrise = crossing(HORIZON, true),
            goldenMorningEnd = crossing(6.0, true),
            noon = times[top],
            noonElevation = heights[top],
            goldenEveningStart = crossing(6.0, false),
            sunset = crossing(HORIZON, false),
            dusk = crossing(-6.0, false),
            alwaysUp = heights.all { it > HORIZON },
            alwaysDown = heights.all { it <= HORIZON },
        )
    }

    /** Refraction and the sun's radius: the top edge touches the horizon. */
    const val HORIZON = -0.833
}

class SunDay(
    val dawn: Long?,
    val sunrise: Long?,
    val goldenMorningEnd: Long?,
    val noon: Long,
    val noonElevation: Double,
    val goldenEveningStart: Long?,
    val sunset: Long?,
    val dusk: Long?,
    val alwaysUp: Boolean,
    val alwaysDown: Boolean,
) {
    /** Daylight in minutes; null when the sun never rises or never sets. */
    val lengthMinutes: Long? get() = if (sunrise != null && sunset != null && sunset > sunrise) (sunset - sunrise) / 60_000 else null
}

/** Phase of the moon from the mean lunar month: right to within a day, which is all a date needs. */
object Moon {
    private const val MONTH_DAYS = 29.530588853
    private const val MONTH_MS = MONTH_DAYS * 86_400_000.0
    /** A new moon: 6 January 2000, 18:14 UTC. */
    private const val NEW_MOON = 947_182_440_000L

    /** 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
    fun phase(epochMillis: Long): Double = ((epochMillis - NEW_MOON) / MONTH_MS).mod(1.0)

    /** Lit share of the disc, 0..1. */
    fun illumination(epochMillis: Long): Double = (1 - cos(2 * PI * phase(epochMillis))) / 2

    /** One of eight names: 0 new moon, 1 waxing crescent, … 4 full moon, … 7 waning crescent. */
    fun phaseIndex(epochMillis: Long): Int = ((phase(epochMillis) * 8 + 0.5).toInt()) % 8

    /** Next moment the phase reaches [target] (0 for a new moon, 0.5 for a full moon). */
    fun next(epochMillis: Long, target: Double): Long {
        val ahead = (target - phase(epochMillis)).mod(1.0)
        return epochMillis + (ahead * MONTH_MS).toLong()
    }
}
