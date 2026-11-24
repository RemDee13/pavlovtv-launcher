package io.github.remdee13.pavlovtv.ui.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.remdee13.pavlovtv.data.Place
import io.github.remdee13.pavlovtv.data.Sun

/** Colors of the sky for a sun height: day blue, golden hour, sunset glow, blue hour, night. */
data class Sky(val top: Color, val bottom: Color, val ink: Color, val night: Float, val sunUp: Boolean) {
    companion object {
        private class Stop(val elevation: Float, val top: Color, val bottom: Color)

        private val stops = listOf(
            Stop(-18f, Color(0xFF070B1C), Color(0xFF141C38)),
            Stop(-9f, Color(0xFF111A3D), Color(0xFF2E2F66)),
            Stop(-4f, Color(0xFF24306A), Color(0xFF7A4E8C)),
            Stop(-0.5f, Color(0xFF3B4A8C), Color(0xFFF08A5D)),
            Stop(4f, Color(0xFF5E93D1), Color(0xFFFFC07A)),
            Stop(12f, Color(0xFF7DB6F2), Color(0xFFE4F1FF)),
            Stop(30f, Color(0xFF8CC4FF), Color(0xFFF4F9FF)),
        )

        fun at(elevation: Double): Sky {
            val e = elevation.toFloat().coerceIn(stops.first().elevation, stops.last().elevation)
            val i = stops.indexOfLast { it.elevation <= e }.coerceIn(0, stops.size - 2)
            val a = stops[i]
            val b = stops[i + 1]
            val t = ((e - a.elevation) / (b.elevation - a.elevation)).coerceIn(0f, 1f)
            val top = lerp(a.top, b.top, t)
            val bottom = lerp(a.bottom, b.bottom, t)
            // Dark hands on a bright day dial, white ones once the sun is low.
            val ink = if (e > 6f) Color(0xFF17202F) else Color.White
            val night = ((-e - 2f) / 10f).coerceIn(0f, 1f)
            return Sky(top, bottom, ink, night, e > -0.83f)
        }

        /** Sky for a place; places without coordinates fall back to the local hour. */
        fun of(place: Place, now: Long, localHour: Int): Sky =
            if (place.hasCoordinates) {
                at(Sun.elevation(place.lat, place.lon, now))
            } else {
                at(if (localHour in 7..17) 25.0 else if (localHour == 6 || localHour == 18) 1.0 else -15.0)
            }
    }
}

private val ZoneCoordinates = mapOf(
    "Europe/London" to (51.51 to -0.13), "Europe/Paris" to (48.86 to 2.35), "Europe/Berlin" to (52.52 to 13.40),
    "Europe/Madrid" to (40.42 to -3.70), "Europe/Rome" to (41.90 to 12.50), "Europe/Lisbon" to (38.72 to -9.14),
    "Europe/Istanbul" to (41.01 to 28.98), "Europe/Moscow" to (55.76 to 37.62), "Europe/Kyiv" to (50.45 to 30.52),
    "Europe/Kiev" to (50.45 to 30.52), "Europe/Minsk" to (53.90 to 27.56), "America/New_York" to (40.71 to -74.01),
    "America/Chicago" to (41.88 to -87.63), "America/Los_Angeles" to (34.05 to -118.24), "America/Toronto" to (43.65 to -79.38),
    "America/Mexico_City" to (19.43 to -99.13), "America/Sao_Paulo" to (-23.55 to -46.63),
    "America/Argentina/Buenos_Aires" to (-34.60 to -58.38), "Asia/Tokyo" to (35.68 to 139.69), "Asia/Seoul" to (37.57 to 126.98),
    "Asia/Shanghai" to (31.23 to 121.47), "Asia/Hong_Kong" to (22.32 to 114.17), "Asia/Singapore" to (1.35 to 103.82),
    "Asia/Bangkok" to (13.76 to 100.50), "Asia/Jakarta" to (-6.20 to 106.85), "Asia/Kolkata" to (22.57 to 88.36),
    "Asia/Dubai" to (25.20 to 55.27), "Asia/Almaty" to (43.24 to 76.89), "Asia/Yekaterinburg" to (56.84 to 60.61),
    "Asia/Novosibirsk" to (55.03 to 82.92), "Asia/Vladivostok" to (43.12 to 131.89), "Australia/Sydney" to (-33.87 to 151.21),
)

/**
 * Gives a clock the coordinates its sky needs. A weather city in the same time zone wins
 * (so the local clock follows the city the user actually lives in); otherwise the zone's city.
 */
fun Place.forSky(weatherPlaces: List<Place>, useWeatherName: Boolean): Place {
    if (hasCoordinates) return this
    weatherPlaces.firstOrNull { it.timezone == timezone && it.hasCoordinates }?.let { city ->
        return copy(name = if (useWeatherName) city.name else name, lat = city.lat, lon = city.lon)
    }
    val (lat, lon) = ZoneCoordinates[timezone] ?: return this
    return copy(lat = lat, lon = lon)
}
