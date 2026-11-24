package io.github.remdee13.pavlovtv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import io.github.remdee13.pavlovtv.data.AppRepository
import io.github.remdee13.pavlovtv.data.FeedFinder
import io.github.remdee13.pavlovtv.data.GeoRepository
import io.github.remdee13.pavlovtv.data.Http
import io.github.remdee13.pavlovtv.data.NewsRepository
import io.github.remdee13.pavlovtv.data.RecommendationsRepository
import io.github.remdee13.pavlovtv.data.SettingsStore
import io.github.remdee13.pavlovtv.data.WeatherRepository
import io.github.remdee13.pavlovtv.wallpaper.ImageFeeds
import io.github.remdee13.pavlovtv.wallpaper.PhotoLibrary
import io.github.remdee13.pavlovtv.wallpaper.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PavlovApp : Application(), ImageLoaderFactory {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(graph.http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(80L * 1024 * 1024)
                .build()
        }
        .allowRgb565(true)
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}

/** Hand-written service locator: the app is small enough not to need a DI framework. */
class Graph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = Http(app)
    val settings = SettingsStore(app, scope)
    val apps = AppRepository(app, scope)
    val geo = GeoRepository(http)
    val weather = WeatherRepository(app, http, geo)
    val news = NewsRepository(app, http)
    val feedFinder = FeedFinder(http)
    val recommendations = RecommendationsRepository(app)
    val photos = PhotoLibrary(app)
    val imageFeeds = ImageFeeds(app, http)
    val wallpaper = WallpaperRepository(app, imageFeeds, photos, settings, scope)
}

val Context.graph: Graph get() = (applicationContext as PavlovApp).graph
