package io.github.remdee13.pavlovtv.wallpaper

import android.content.Context
import io.github.remdee13.pavlovtv.data.AppLocale
import io.github.remdee13.pavlovtv.data.Http
import io.github.remdee13.pavlovtv.data.OnlineSource
import io.github.remdee13.pavlovtv.data.SettingsStore
import io.github.remdee13.pavlovtv.data.WallpaperConfig
import io.github.remdee13.pavlovtv.data.WallpaperKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate

sealed interface WallpaperModel {
    data class Gradient(val index: Int) : WallpaperModel

    /** [data] is a File or an http(s) URL string, anything Coil can load. */
    data class Image(val data: Any, val caption: String = "") : WallpaperModel
}

@Serializable
private data class OnlinePick(val source: OnlineSource, val day: String, val url: String, val caption: String)

class WallpaperRepository(
    private val context: Context,
    private val feeds: ImageFeeds,
    private val photos: PhotoLibrary,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val pickFile = File(context.filesDir, "wallpaper_pick.json")
    private val mutex = Mutex()
    private val _current = MutableStateFlow(initial(settings.current.wallpaper))
    val current: StateFlow<WallpaperModel> = _current

    init {
        scope.launch {
            // dim does not change which picture is shown
            settings.flow.map { it.wallpaper.copy(dim = 0) }.distinctUntilChanged().collectLatest { resolve(it, next = false) }
        }
    }

    private fun readPick(): OnlinePick? = runCatching { Http.json.decodeFromString<OnlinePick>(pickFile.readText()) }.getOrNull()

    private fun initial(config: WallpaperConfig): WallpaperModel = when (config.kind) {
        WallpaperKind.GRADIENT -> WallpaperModel.Gradient(config.gradient)
        WallpaperKind.PHOTO -> File(config.photoPath).takeIf { it.exists() }?.let { WallpaperModel.Image(it) } ?: WallpaperModel.Gradient(config.gradient)
        WallpaperKind.URL -> if (config.url.isNotBlank()) WallpaperModel.Image(config.url) else WallpaperModel.Gradient(config.gradient)
        WallpaperKind.ONLINE -> readPick()?.takeIf { it.source == config.online }?.let { WallpaperModel.Image(it.url, it.caption) }
            ?: WallpaperModel.Gradient(config.gradient)
    }

    /** Shows another picture from the current online source. */
    fun next() {
        scope.launch { resolve(settings.current.wallpaper, next = true) }
    }

    /** Online wallpapers change once a day; call this when the launcher comes to front. */
    fun checkDaily() {
        val config = settings.current.wallpaper
        if (config.kind != WallpaperKind.ONLINE) return
        val pick = readPick()
        if (pick == null || pick.source != config.online || pick.day != LocalDate.now().toString()) {
            scope.launch { resolve(config, next = false) }
        }
    }

    private suspend fun resolve(config: WallpaperConfig, next: Boolean) = mutex.withLock {
        if (config.kind != WallpaperKind.ONLINE) {
            _current.value = initial(config)
            return@withLock
        }
        val today = LocalDate.now().toString()
        val previous = withContext(Dispatchers.IO) { readPick() }
        if (!next && previous != null && previous.source == config.online && previous.day == today) {
            _current.value = WallpaperModel.Image(previous.url, previous.caption)
            return@withLock
        }
        val language = AppLocale.effectiveLanguage(context)
        val images = feeds.images(config.online, language)
        if (images.isEmpty()) {
            // Offline: keep whatever we showed last time for this source.
            _current.value = previous?.takeIf { it.source == config.online }?.let { WallpaperModel.Image(it.url, it.caption) }
                ?: WallpaperModel.Gradient(config.gradient)
            return@withLock
        }
        val sameSource = previous?.source == config.online
        val pick = when (config.online) {
            // "Picture of the day" sources start with today's picture and then walk back in time.
            OnlineSource.BING, OnlineSource.WIKIMEDIA -> if (next && sameSource) {
                val index = images.indexOfFirst { it.url == previous?.url }
                images[(index + 1).mod(images.size)]
            } else {
                images.first()
            }
            else -> images.filter { it.url != previous?.url }.randomOrNull() ?: images.first()
        }
        withContext(Dispatchers.IO) {
            runCatching { pickFile.writeText(Http.json.encodeToString(OnlinePick(config.online, today, pick.url, pick.caption))) }
        }
        _current.value = WallpaperModel.Image(pick.url, pick.caption)
    }

    /** Playlist for the screensaver. */
    suspend fun playlist(source: OnlineSource): List<RemoteImage> =
        feeds.images(source, AppLocale.effectiveLanguage(context))

    fun myPhotos(): List<File> = photos.photos.value
}
