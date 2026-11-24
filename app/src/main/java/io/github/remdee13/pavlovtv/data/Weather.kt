package io.github.remdee13.pavlovtv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/** City search and rough location by IP. Both services are free and need no key. */
class GeoRepository(private val http: Http) {
    suspend fun search(query: String, language: String): List<Place> {
        if (query.isBlank()) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Http.encode(query.trim())}&count=12&language=$language&format=json"
        val results = http.getJson(url).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val o = element.jsonObject
            val name = o.str("name") ?: return@mapNotNull null
            Place(
                name = name,
                country = listOfNotNull(o.str("admin1"), o.str("country")).distinct().joinToString(", "),
                lat = o.num("latitude") ?: return@mapNotNull null,
                lon = o.num("longitude") ?: return@mapNotNull null,
                timezone = o.str("timezone") ?: "UTC",
            )
        }
    }

    /** Approximate city from the internet connection, via GeoJS (free, no key). */
    suspend fun locateByIp(): Place? = runCatching { geoJs() }.getOrNull()

    private suspend fun geoJs(): Place? {
        val o = http.getJson("https://get.geojs.io/v1/ip/geo.json").jsonObject
        return Place(o.str("city") ?: return null, o.str("country") ?: "", o.num("latitude") ?: return null, o.num("longitude") ?: return null, o.str("timezone") ?: "")
    }
}

@Serializable
data class WeatherNow(
    val temp: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val wind: Double,
)

@Serializable
data class WeatherDay(val date: String, val code: Int, val max: Double, val min: Double)

@Serializable
data class WeatherReport(val place: Place, val now: WeatherNow, val days: List<WeatherDay>)

@Serializable
data class WeatherBundle(
    val key: String,
    val main: WeatherReport?,
    val extra: List<WeatherReport>,
    val unit: TempUnit,
    val fetchedAt: Long,
)

/** Forecasts from Open-Meteo (https://open-meteo.com, CC BY 4.0). */
class WeatherRepository(context: Context, private val http: Http, private val geo: GeoRepository) {
    private val cacheFile = File(context.filesDir, "weather.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(readCache())
    val state: StateFlow<WeatherBundle?> = _state
    private var autoPlace: Place? = null

    private fun readCache(): WeatherBundle? =
        runCatching { Http.json.decodeFromString<WeatherBundle>(cacheFile.readText()) }.getOrNull()

    private fun keyFor(s: LauncherSettings): String = buildString {
        append(if (s.weatherAuto || s.weatherPlace == null) "auto" else s.weatherPlace.key)
        s.extraPlaces.forEach { append(';').append(it.key) }
        append('|').append(s.tempUnit)
    }

    suspend fun refresh(settings: LauncherSettings, force: Boolean = false) = mutex.withLock {
        val key = keyFor(settings)
        val cached = _state.value
        val fresh = cached != null && cached.key == key && System.currentTimeMillis() - cached.fetchedAt < MAX_AGE_MS
        if (fresh && !force) return@withLock
        runCatching {
            val mainPlace = if (settings.weatherAuto || settings.weatherPlace == null) {
                autoPlace ?: geo.locateByIp()?.also { autoPlace = it } ?: cached?.main?.place
            } else {
                settings.weatherPlace
            }
            coroutineScope {
                val main = mainPlace?.let { p -> async { runCatching { fetch(p, settings.tempUnit) }.getOrNull() } }
                val extra = settings.extraPlaces.map { p -> async { runCatching { fetch(p, settings.tempUnit) }.getOrNull() } }
                val bundle = WeatherBundle(
                    key = key,
                    main = main?.await(),
                    extra = extra.awaitAll().filterNotNull(),
                    unit = settings.tempUnit,
                    fetchedAt = System.currentTimeMillis(),
                )
                if (bundle.main != null || bundle.extra.isNotEmpty()) {
                    _state.value = bundle
                    withContext(Dispatchers.IO) { cacheFile.writeText(Http.json.encodeToString(bundle)) }
                }
            }
        }
    }

    private suspend fun fetch(place: Place, unit: TempUnit): WeatherReport {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?latitude=").append(place.lat)
            append("&longitude=").append(place.lon)
            append("&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min")
            append("&timezone=auto&forecast_days=5")
            if (unit == TempUnit.FAHRENHEIT) append("&temperature_unit=fahrenheit&wind_speed_unit=mph")
        }
        val root = http.getJson(url).jsonObject
        val current = root["current"]!!.jsonObject
        val daily = root["daily"]!!.jsonObject
        val dates = daily["time"]!!.jsonArray.map { it.primitiveOrNull().orEmpty() }
        val codes = daily["weather_code"]!!.jsonArray.map { it.primitiveOrNull()?.toDoubleOrNull()?.toInt() ?: 0 }
        val maxes = daily["temperature_2m_max"]!!.jsonArray.map { it.primitiveOrNull()?.toDoubleOrNull() ?: 0.0 }
        val mins = daily["temperature_2m_min"]!!.jsonArray.map { it.primitiveOrNull()?.toDoubleOrNull() ?: 0.0 }
        val timezone = root.str("timezone") ?: place.timezone
        return WeatherReport(
            place = if (place.timezone.isEmpty()) place.copy(timezone = timezone) else place,
            now = WeatherNow(
                temp = current.num("temperature_2m") ?: 0.0,
                feelsLike = current.num("apparent_temperature") ?: 0.0,
                code = current.num("weather_code")?.toInt() ?: 0,
                isDay = (current.num("is_day") ?: 1.0) > 0.5,
                humidity = current.num("relative_humidity_2m")?.toInt() ?: 0,
                wind = current.num("wind_speed_10m") ?: 0.0,
            ),
            days = dates.indices.map { i -> WeatherDay(dates[i], codes.getOrElse(i) { 0 }, maxes.getOrElse(i) { 0.0 }, mins.getOrElse(i) { 0.0 }) },
        )
    }

    companion object {
        private const val MAX_AGE_MS = 30 * 60 * 1000L
    }
}

/** Icon families for WMO weather codes. */
enum class WeatherGlyph { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SLEET, THUNDER }

fun glyphFor(code: Int): WeatherGlyph = when (code) {
    0, 1 -> WeatherGlyph.CLEAR
    2 -> WeatherGlyph.PARTLY
    3 -> WeatherGlyph.CLOUDY
    45, 48 -> WeatherGlyph.FOG
    51, 53, 55 -> WeatherGlyph.DRIZZLE
    56, 57, 66, 67 -> WeatherGlyph.SLEET
    61, 63, 65, 80, 81, 82 -> WeatherGlyph.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherGlyph.SNOW
    95, 96, 99 -> WeatherGlyph.THUNDER
    else -> WeatherGlyph.CLOUDY
}
