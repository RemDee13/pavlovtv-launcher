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
import java.time.ZoneId

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
data class WorldClock(val place: Place, val style: ClockStyle = ClockStyle.ANALOG)

@Serializable
enum class TempUnit { CELSIUS, FAHRENHEIT }

/** Visual style of every panel, card, button and dialog. */
@Serializable
enum class UiStyle { GLASS, SOLID }

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
    val motion: Boolean = true,
)

@Serializable
data class CustomFeed(val name: String, val url: String)

@Serializable
data class LauncherSettings(
    val uiStyle: UiStyle = UiStyle.GLASS,
    // Weather
    val weatherAuto: Boolean = true,
    val weatherPlace: Place? = null,
    val extraPlaces: List<Place> = emptyList(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    // Clocks; null means "not configured yet, use defaults"
    val clocks: List<WorldClock>? = null,
    val use24h: Boolean = true,
    // News; null means "defaults for the UI language"
    val newsSources: List<String>? = null,
    val customFeeds: List<CustomFeed> = emptyList(),
    // Apps; null favorites means "first TV apps"
    val favorites: List<String>? = null,
    val hidden: List<String> = emptyList(),
    val showPhoneApps: Boolean = true,
    // Home screen sections
    val showWeather: Boolean = true,
    val showClocks: Boolean = true,
    val showNews: Boolean = true,
    val showRecommendations: Boolean = true,
    val hiddenChannels: List<Long> = emptyList(),
    /** Show only the "Continue watching" row and hide every app's own rows. */
    val recOnlyWatchNext: Boolean = false,
    // Looks
    val wallpaper: WallpaperConfig = WallpaperConfig(),
    val screensaver: ScreensaverConfig = ScreensaverConfig(),
) {
    val effectiveClocks: List<WorldClock> get() = clocks ?: defaultClocks()

    companion object {
        const val MAX_CLOCKS = 4
        const val MAX_EXTRA_PLACES = 3

        fun defaultClocks(): List<WorldClock> {
            val local = ZoneId.systemDefault().id
            val zones = (listOf(local) + listOf("Europe/London", "America/New_York", "Asia/Tokyo", "Europe/Moscow"))
                .distinct()
                .take(MAX_CLOCKS)
            return zones.mapIndexed { index, zone ->
                WorldClock(
                    place = Place(name = cityNameForZone(zone), timezone = zone),
                    style = if (index == 0) ClockStyle.DIGITAL else ClockStyle.ANALOG,
                )
            }
        }
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
