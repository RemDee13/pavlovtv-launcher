package io.github.remdee13.pavlovtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Place(
    val name: String,
    val country: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val timezone: String = "",
) {
    val hasCoordinates: Boolean get() = !(lat == 0.0 && lon == 0.0)
    val key: String get() = "$name|$lat|$lon|$timezone"
}

@Serializable
enum class ClockStyle { ANALOG, DIGITAL }

@Serializable
enum class TempUnit { CELSIUS, FAHRENHEIT }

/** Visual style of every panel, card, button and dialog. */
@Serializable
enum class UiStyle { GLASS, DARK, SOLID }

@Serializable
enum class OnlineSource { BING, PICSUM, WIKIMEDIA, ART, NASA }

@Serializable
enum class WallpaperKind { GRADIENT, ONLINE, PHOTO, URL }

@Serializable
data class WallpaperConfig(
    val kind: WallpaperKind = WallpaperKind.ONLINE,
    val online: OnlineSource = OnlineSource.BING,
    val gradient: Int = 0,
    val photoPath: String = "",
    val url: String = "",
    /** Extra darkening on top of the wallpaper, 0..80 percent. */
    val dim: Int = 20,
)

@Serializable
enum class SaverMode { WALLPAPER, ONLINE, MY_PHOTOS }

@Serializable
data class ScreensaverConfig(
    val mode: SaverMode = SaverMode.ONLINE,
    val online: OnlineSource = OnlineSource.BING,
    val intervalSec: Int = 30,
    val showClock: Boolean = true,
    val showWeather: Boolean = true,
    val showRates: Boolean = true,
    val motion: Boolean = true,
)

@Serializable
data class CustomFeed(val name: String, val url: String)

@Serializable
data class LauncherSettings(
    val uiStyle: UiStyle = UiStyle.GLASS,
    // Cities: the main one (found by IP or chosen) and up to nine more, each with a clock and weather.
    val weatherAuto: Boolean = true,
    val weatherPlace: Place? = null,
    val extraPlaces: List<Place> = emptyList(),
    /** Analog or digital clock per city, by [Place.key]; the main city is [MAIN_CITY]. */
    val clockStyles: Map<String, ClockStyle> = emptyMap(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val use24h: Boolean = true,
    // News; null means "defaults for the UI language"
    val newsSources: List<String>? = null,
    val customFeeds: List<CustomFeed> = emptyList(),
    /** News topics found by search ("Tesla", "Формула-1"), any words the user chose. */
    val newsSearches: List<String> = emptyList(),
    // Rates: "USD/THB", "BTC/USD"; null means "defaults for the home currency"
    val favoriteRates: List<String>? = null,
    val showRates: Boolean = true,
    // Apps; null favorites means "first TV apps"
    val favorites: List<String>? = null,
    val hidden: List<String> = emptyList(),
    val showPhoneApps: Boolean = true,
    // Home screen sections
    /** The time and weather widget. */
    val showWeather: Boolean = true,
    val showNews: Boolean = true,
    val showRecommendations: Boolean = true,
    val hiddenChannels: List<Long> = emptyList(),
    /** Show only the "Continue watching" row and hide every app's own rows. */
    val recOnlyWatchNext: Boolean = false,
    // Looks
    val wallpaper: WallpaperConfig = WallpaperConfig(),
    val screensaver: ScreensaverConfig = ScreensaverConfig(),
) {
    companion object {
        /** Besides the main city, so ten cities in all. */
        const val MAX_EXTRA_PLACES = 9
        /** Cities on the home widget, the main one included; every city is on the detail screens. */
        const val HOME_CITIES = 4
        const val MAIN_CITY = "main"
        const val MAX_RATES = 10
        /** Rates on the home widget and the screensaver. */
        const val HOME_RATES = 3
    }
}

/** Localised city name for a time zone id, e.g. "Europe/London" -> "Лондон". */
fun cityNameForZone(zoneId: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val fromIcu = runCatching {
        android.icu.text.TimeZoneNames.getInstance(locale).getExemplarLocationName(zoneId)
    }.getOrNull()
    return fromIcu?.takeIf { it.isNotBlank() } ?: zoneId.substringAfterLast('/').replace('_', ' ')
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private val key = stringPreferencesKey("launcher_settings_v1")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private fun decode(raw: String?): LauncherSettings =
        raw?.let { runCatching { json.decodeFromString<LauncherSettings>(it) }.getOrNull() } ?: LauncherSettings()

    // Read once synchronously so the first frame already has the user's wallpaper and layout.
    private val initial: LauncherSettings = runBlocking {
        runCatching { decode(context.dataStore.data.first()[key]) }.getOrDefault(LauncherSettings())
    }

    val flow: StateFlow<LauncherSettings> = context.dataStore.data
        .map { decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    val current: LauncherSettings get() = flow.value

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(decode(prefs[key])))
        }
    }
}
