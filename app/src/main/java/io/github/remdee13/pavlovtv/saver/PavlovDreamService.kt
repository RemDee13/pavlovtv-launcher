package io.github.remdee13.pavlovtv.saver

import android.content.Context
import android.service.dreams.DreamService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.remdee13.pavlovtv.data.AppLocale
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.rateIds
import io.github.remdee13.pavlovtv.graph
import io.github.remdee13.pavlovtv.ui.rates.rememberFavoriteRates
import io.github.remdee13.pavlovtv.ui.theme.PavlovTheme

/**
 * System screensaver ("Daydream"). Users pick it in Android Settings, so it never
 * replaces the TV's own screensaver unless they choose to.
 */
class PavlovDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val graph = applicationContext.graph
        val view = ComposeView(this).apply {
            setContent {
                PavlovTheme {
                    val settings by graph.settings.flow.collectAsState()
                    val weather by graph.weather.state.collectAsState()
                    val slides by produceState(emptyList<Slide>(), settings.screensaver, settings.wallpaper) {
                        value = loadSlides(graph, settings)
                    }
                    val rates = rememberFavoriteRates(
                        graph.rates.quotes,
                        graph.rates::refresh,
                        if (settings.screensaver.showRates) settings.rateIds.take(LauncherSettings.HOME_RATES) else emptyList(),
                    )
                    if (slides.isNotEmpty()) Screensaver(slides, settings, weather, rates)
                }
            }
        }
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
