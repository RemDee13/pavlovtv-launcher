package io.github.remdee13.pavlovtv.data

import android.icu.util.ULocale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** High or low water. */
class TideTurn(val time: Long, val height: Double, val high: Boolean)

/** The sea next to a city: a modelled tide curve (hourly), water temperature and waves. */
class SeaReport(
    val times: List<Long>,
    val heights: List<Double>,
    val turns: List<TideTurn>,
    val waterTemp: Double?,
    val waveHeight: Double?,
    val wavePeriod: Double?,
) {
    /** Tides worth drawing; seas like the Black Sea barely move. */
    val hasTide: Boolean get() = heights.size > 6 && (heights.max() - heights.min()) > 0.2
}

class AirReport(val usAqi: Int, val pm25: Double?)

class Holiday(val date: LocalDate, val name: String)

/** Extra facts for the world clock screen. Every part is optional: inland cities have no sea. */
class CityInfo(val sea: SeaReport?, val air: AirReport?, val holiday: Holiday?)

/**
 * Local facts for a city on the world clock screen. Only the city's coordinates and its
 * country code are sent; results are kept in memory for an hour.
 */
class LocalInfoRepository(private val http: Http, private val geo: GeoRepository) {
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Long, CityInfo>>()
    private val located = HashMap<String, Place?>()
    private var rates: Pair<Long, Map<String, Double>>? = null

    /**
     * Coordinates for a clock that only knows its time zone ("Asia/Tokyo"): the zone's
     * city, looked up by name and accepted only when it is in the same zone.
     */
    suspend fun locate(place: Place, language: String): Place? {
        if (place.hasCoordinates) return place
        val zone = place.timezone.ifEmpty { return null }
        mutex.withLock { if (located.containsKey(zone)) return located[zone] }
        val name = zone.substringAfterLast('/').replace('_', ' ')
        val found = runCatching { geo.search(name, language) }.getOrDefault(emptyList())
            .firstOrNull { it.timezone == zone }
            ?.let { place.copy(lat = it.lat, lon = it.lon, country = place.country.ifEmpty { it.country }) }
        mutex.withLock { located[zone] = found }
        return found
    }

    suspend fun info(place: Place, language: String): CityInfo {
        val key = "${place.lat},${place.lon}|$language"
        mutex.withLock {
            cache[key]?.let { (at, info) -> if (System.currentTimeMillis() - at < MAX_AGE_MS) return info }
        }
        val info = coroutineScope {
            val sea = async { runCatching { sea(place) }.getOrNull() }
            val air = async { runCatching { air(place) }.getOrNull() }
            val holiday = async { runCatching { holiday(place, language) }.getOrNull() }
            CityInfo(sea.await(), air.await(), holiday.await())
        }
        mutex.withLock { cache[key] = System.currentTimeMillis() to info }
        return info
    }

    /** Units of each currency per US dollar. */
    suspend fun rates(): Map<String, Double>? {
        mutex.withLock {
            rates?.let { (at, map) -> if (System.currentTimeMillis() - at < RATES_AGE_MS) return map }
        }
        val map = runCatching {
            val root = http.getJson("https://open.er-api.com/v6/latest/USD").jsonObject
            root["rates"]!!.jsonObject.mapValues { (_, v) -> v.primitiveOrNull()?.toDoubleOrNull() ?: 0.0 }.filterValues { it > 0 }
        }.getOrNull() ?: return rates?.second
        mutex.withLock { rates = System.currentTimeMillis() to map }
        return map
    }

    private suspend fun sea(place: Place): SeaReport? {
        val root = http.getJson(
            "https://marine-api.open-meteo.com/v1/marine?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=sea_surface_temperature,wave_height,wave_period&hourly=sea_level_height_msl" +
                "&past_hours=2&forecast_hours=30&timeformat=unixtime",
        ).jsonObject
        val current = root["current"]?.jsonObject
        val hourly = root["hourly"]?.jsonObject
        val rawTimes = hourly?.get("time")?.jsonArray?.map { (it.primitiveOrNull()?.toLongOrNull() ?: 0L) * 1000 }.orEmpty()
        val rawHeights = hourly?.get("sea_level_height_msl")?.jsonArray?.map { it.primitiveOrNull()?.toDoubleOrNull() }.orEmpty()
        val pairs = rawTimes.zip(rawHeights).filter { it.second != null }.map { it.first to it.second!! }
        val water = current?.num("sea_surface_temperature")
        val waves = current?.num("wave_height")
        // Inland points come back as nulls: no sea here.
        if (pairs.isEmpty() && water == null && waves == null) return null
        val times = pairs.map { it.first }
        val heights = pairs.map { it.second }
        return SeaReport(times, heights, turns(times, heights), water, waves, current?.num("wave_period"))
    }

    private suspend fun air(place: Place): AirReport? {
        val root = http.getJson(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${place.lat}&longitude=${place.lon}&current=us_aqi,pm2_5",
        ).jsonObject
        val current = root["current"]?.jsonObject ?: return null
        val aqi = current.num("us_aqi")?.toInt() ?: return null
        return AirReport(aqi, current.num("pm2_5"))
    }

    private suspend fun holiday(place: Place, language: String): Holiday? {
        val country = regionOf(place.timezone) ?: return null
        val body = http.getString("https://date.nager.at/api/v3/NextPublicHolidays/$country")
        val first = Http.json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return null
        val date = runCatching { LocalDate.parse(first.str("date")) }.getOrNull() ?: return null
        // The local name when the screen speaks the country's language, the English one otherwise.
        val speaksIt = runCatching { ULocale.addLikelySubtags(ULocale("und_$country")).language == language }.getOrDefault(false)
        val name = (if (speaksIt) first.str("localName") else first.str("name")) ?: first.str("name") ?: return null
        return Holiday(date, name)
    }

    companion object {
        private const val MAX_AGE_MS = 60 * 60_000L
        private const val RATES_AGE_MS = 12 * 60 * 60_000L

        /** Two-letter country of a time zone ("Asia/Bangkok" -> "TH"), or null for "UTC" and the like. */
        fun regionOf(zoneId: String): String? =
            runCatching { android.icu.util.TimeZone.getRegion(zoneId) }.getOrNull()?.takeIf { it.length == 2 }

        fun currencyOf(zoneId: String): Currency? =
            regionOf(zoneId)?.let { runCatching { Currency.getInstance(Locale("", it)) }.getOrNull() }

        fun homeZone(): String = ZoneId.systemDefault().id

        /** Highs and lows of an hourly curve, timed between the hours with a parabola. */
        fun turns(times: List<Long>, heights: List<Double>): List<TideTurn> {
            val out = ArrayList<TideTurn>()
            for (i in 1 until heights.size - 1) {
                val a = heights[i - 1]
                val b = heights[i]
                val c = heights[i + 1]
                val high = b > a && b >= c
                val low = b < a && b <= c
                if (!high && !low) continue
                val curve = a - 2 * b + c
                val shift = if (abs(curve) > 1e-9) ((a - c) / (2 * curve)).coerceIn(-0.5, 0.5) else 0.0
                val step = times[i + 1] - times[i]
                val height = b - (a - c) * shift / 4
                out += TideTurn(times[i] + (shift * step).toLong(), height, high)
            }
            return out
        }
    }
}

