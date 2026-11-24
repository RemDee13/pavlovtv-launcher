package io.github.remdee13.pavlovtv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.remdee13.pavlovtv.data.AppEntry
import io.github.remdee13.pavlovtv.data.AppLocale
import io.github.remdee13.pavlovtv.data.ChartRange
import io.github.remdee13.pavlovtv.data.Article
import io.github.remdee13.pavlovtv.data.CityInfo
import io.github.remdee13.pavlovtv.data.CustomFeed
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.Place
import io.github.remdee13.pavlovtv.data.RateSeries
import io.github.remdee13.pavlovtv.graph
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app.graph

    val settings: StateFlow<LauncherSettings> = g.settings.flow
    val allApps: StateFlow<List<AppEntry>> = g.apps.apps

    val visibleApps: StateFlow<List<AppEntry>> = combine(allApps, settings) { apps, s ->
        apps.filter { it.key !in s.hidden && (s.showPhoneApps || it.isTv) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<AppEntry>> = combine(visibleApps, settings) { apps, s ->
        val byKey = apps.associateBy { it.key }
        (s.favorites ?: defaultFavorites(apps)).mapNotNull { byKey[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppEntry>> = combine(allApps, settings) { apps, s ->
        apps.filter { it.key in s.hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val weather = g.weather.state
    val news = g.news.items
    val newsLoading = g.news.loading
    val recStatus = g.recommendations.status
    val allRecRows = g.recommendations.rows
    val recRows = combine(allRecRows, settings) { rows, s ->
        when {
            !s.showRecommendations -> emptyList()
            s.recOnlyWatchNext -> rows.filter { it.isWatchNext && it.id !in s.hiddenChannels }
            else -> rows.filter { it.id !in s.hiddenChannels }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val wallpaper = g.wallpaper.current
    val photos = g.photos.photos

    val language: String get() = AppLocale.effectiveLanguage(getApplication())

    init {
        // Re-fetch right away when the user changes cities, units or feeds.
        viewModelScope.launch {
            settings.map { Triple(it.weatherAuto, it.weatherPlace, it.extraPlaces to it.tempUnit) }
                .distinctUntilChanged().drop(1)
                .collect { g.weather.refresh(settings.value, force = true) }
        }
        viewModelScope.launch {
            settings.map { Triple(it.newsSources, it.customFeeds, it.newsSearches) }
                .distinctUntilChanged().drop(1)
                // Several topics are often switched in a row: fetch once they settle.
                .debounce(1200)
                .collect { g.news.refresh(settings.value, language, force = true) }
        }
    }

    fun onResume() {
        g.apps.reload()
        g.wallpaper.checkDaily()
        viewModelScope.launch { g.weather.refresh(settings.value) }
        viewModelScope.launch { g.news.refresh(settings.value, language) }
        viewModelScope.launch { g.recommendations.refresh(force = true) }
    }

    fun refreshNews() = viewModelScope.launch { g.news.refresh(settings.value, language, force = true) }
    fun refreshWeather() = viewModelScope.launch { g.weather.refresh(settings.value, force = true) }

    suspend fun article(url: String): Article? = g.articles.load(url)

    suspend fun previewImage(url: String): String? = g.articles.previewImage(url)

    /** Coordinates for a clock that only knows its time zone. */
    suspend fun locatePlace(place: Place): Place? = g.localInfo.locate(place, language)

    suspend fun cityInfo(place: Place): CityInfo = g.localInfo.info(place, language)

    suspend fun currencyRates(): Map<String, Double>? = g.localInfo.rates()

    /** The last 24 hours of every pair asked for so far, for the widget, chips and screensaver. */
    val rateQuotes: StateFlow<Map<String, RateSeries>> = g.rates.quotes

    suspend fun refreshRates(ids: List<String>) = g.rates.refresh(ids)

    suspend fun rateSeries(id: String, range: ChartRange): RateSeries? = g.rates.series(id, range)
    fun refreshRecommendations() = viewModelScope.launch { g.recommendations.refresh(force = true) }

    /**
     * Finds a feed for whatever the user typed (site, page, RSS link) and adds it to their
     * sources. Returns the added feed, or null when no feed was found.
     */
    suspend fun addFeed(input: String): CustomFeed? {
        val feed = g.feedFinder.find(input) ?: return null
        g.settings.update { st -> st.copy(customFeeds = (st.customFeeds.filter { it.url != feed.url } + feed)) }
        return feed
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { g.settings.update(transform) }
    }

    private fun currentFavoriteKeys(s: LauncherSettings): List<String> = s.favorites ?: favorites.value.map { it.key }

    fun toggleFavorite(app: AppEntry) = update { s ->
        val keys = currentFavoriteKeys(s)
        s.copy(favorites = if (app.key in keys) keys - app.key else keys + app.key)
    }

    fun moveFavorite(app: AppEntry, delta: Int) = update { s ->
        val keys = currentFavoriteKeys(s).toMutableList()
        val from = keys.indexOf(app.key)
        if (from < 0) return@update s
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        keys.removeAt(from)
        keys.add(to, app.key)
        s.copy(favorites = keys)
    }

    fun hide(app: AppEntry) = update { s ->
        s.copy(hidden = (s.hidden + app.key).distinct(), favorites = s.favorites?.minus(app.key))
    }

    fun unhide(app: AppEntry) = update { s -> s.copy(hidden = s.hidden - app.key) }

    suspend fun searchPlaces(query: String): List<Place> = runCatching { g.geo.search(query, language) }.getOrDefault(emptyList())

    fun nextWallpaper() = g.wallpaper.next()

    fun deletePhoto(file: java.io.File) {
        g.photos.delete(file)
        if (settings.value.wallpaper.photoPath == file.absolutePath) {
            update { it.copy(wallpaper = it.wallpaper.copy(photoPath = g.photos.photos.value.firstOrNull()?.absolutePath.orEmpty())) }
        }
    }

    companion object {
        /** Popular streaming apps first, so a fresh install already looks useful. */
        private val preferred = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.spotify.tv.android", "com.google.android.youtube.tvmusic",
            "com.plexapp.android", "org.xbmc.kodi", "tv.twitch.android.app", "com.liskovsoft.smarttubetv.beta",
        )

        fun defaultFavorites(apps: List<AppEntry>): List<String> {
            val tvApps = apps.filter { it.isTv }
            val first = preferred.filter { pkg -> tvApps.any { it.key == pkg } }
            val rest = tvApps.filter { !it.isSystem && it.key !in first }.map { it.key }
            val system = tvApps.filter { it.isSystem && it.key !in first }.map { it.key }
            return (first + rest + system).take(8)
        }
    }
}
