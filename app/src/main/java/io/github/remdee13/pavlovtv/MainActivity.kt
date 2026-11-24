package io.github.remdee13.pavlovtv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.github.remdee13.pavlovtv.data.AppEntry
import io.github.remdee13.pavlovtv.data.AppLocale
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.rateIds
import io.github.remdee13.pavlovtv.data.RecommendationsRepository
import io.github.remdee13.pavlovtv.saver.Screensaver
import io.github.remdee13.pavlovtv.saver.Slide
import io.github.remdee13.pavlovtv.saver.loadSlides
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.FocusMemory
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.home.AppOptionsActions
import io.github.remdee13.pavlovtv.ui.home.AppOptionsDialog
import io.github.remdee13.pavlovtv.ui.home.AppVisualCache
import io.github.remdee13.pavlovtv.ui.home.HomeActions
import io.github.remdee13.pavlovtv.ui.home.HomeScreen
import io.github.remdee13.pavlovtv.ui.home.LocalAppVisuals
import io.github.remdee13.pavlovtv.ui.news.NewsScreen
import io.github.remdee13.pavlovtv.ui.rates.RatesScreen
import io.github.remdee13.pavlovtv.ui.rates.rememberFavoriteRates
import io.github.remdee13.pavlovtv.ui.settings.SettingsActions
import io.github.remdee13.pavlovtv.ui.settings.SettingsPage
import io.github.remdee13.pavlovtv.ui.settings.SettingsScreen
import io.github.remdee13.pavlovtv.ui.theme.Backdrop
import io.github.remdee13.pavlovtv.ui.theme.BackdropRenderer
import io.github.remdee13.pavlovtv.ui.theme.LocalBackdrop
import io.github.remdee13.pavlovtv.ui.theme.LocalUiStyle
import io.github.remdee13.pavlovtv.ui.theme.PavlovTheme
import io.github.remdee13.pavlovtv.ui.widgets.ClockScreen
import io.github.remdee13.pavlovtv.ui.widgets.WeatherScreen
import io.github.remdee13.pavlovtv.wallpaper.Gradients
import io.github.remdee13.pavlovtv.wallpaper.WallpaperModel
import io.github.remdee13.pavlovtv.wallpaper.WallpaperComposer
import io.github.remdee13.pavlovtv.wallpaper.drawGradientPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private sealed interface Layer {
    /** [read]: open the story itself; Back then shows all headlines. */
    data class News(val index: Int, val read: Boolean) : Layer
    /** Weather and time of every city; [index] is the city tab, kept when switching between them. */
    data class Weather(val index: Int = 0) : Layer
    data class Clocks(val index: Int = 0) : Layer
    data object Rates : Layer
    data class Settings(val page: SettingsPage) : Layer
    data object SaverPreview : Layer
}

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var visuals: AppVisualCache

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visuals = AppVisualCache(applicationContext)
        setContent {
            PavlovTheme {
                CompositionLocalProvider(LocalAppVisuals provides visuals) {
                    Root(vm, homePressed)
                }
            }
        }
        // The wallpaper layer always covers the screen; a window background under it is wasted GPU work.
        window.setBackgroundDrawable(null)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home: close overlays and jump to the top.
        if (intent.action == Intent.ACTION_MAIN) homePressed.tryEmit(Unit)
    }

    fun start(intent: Intent, fallback: Intent? = null): Boolean {
        val ok = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        return ok || (fallback != null && runCatching { startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess)
    }
}

@Composable
private fun Root(vm: LauncherViewModel, homePressed: SharedFlow<Unit>) {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val graph = activity.graph
    val settings by vm.settings.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()
    val backdrop = remember { Backdrop() }
    val listState = rememberLazyListState()
    val homeMemory = remember { FocusMemory() }
    val scope = rememberCoroutineScope()
    var layer by remember {
        // After a language switch the activity is recreated; reopen settings where the user was.
        val reopen = activity.intent.getStringExtra(EXTRA_REOPEN_SETTINGS)
        activity.intent.removeExtra(EXTRA_REOPEN_SETTINGS)
        mutableStateOf<Layer?>(SettingsPage.entries.firstOrNull { it.name == reopen }?.let { Layer.Settings(it) })
    }
    var appOptions by remember { mutableStateOf<AppEntry?>(null) }
    // Key of the favorite being moved along its row, if any.
    var moving by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(wallpaper) { backdrop.image = BackdropRenderer.render(activity, wallpaper) }
    backdrop.dim = settings.wallpaper.dim / 100f

    LaunchedEffect(Unit) {
        homePressed.collect {
            layer = null
            appOptions = null
            moving = null
            listState.animateScrollToItem(0)
        }
    }
    // Give focus back to whatever was focused before an overlay opened.
    LaunchedEffect(layer, appOptions) {
        if (layer == null && appOptions == null) {
            delay(60)
            homeMemory.restorePinned()
        } else {
            homeMemory.pin()
        }
    }

    val recPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshRecommendations() }
    LaunchedEffect(Unit) {
        val flags = activity.getSharedPreferences("launcher_flags", Context.MODE_PRIVATE)
        if (settings.showRecommendations && !graph.recommendations.hasPermission() && !flags.getBoolean("asked_recs", false)) {
            flags.edit().putBoolean("asked_recs", true).apply()
            delay(1500)
            runCatching { recPermission.launch(RecommendationsRepository.PERMISSION) }
        }
    }

    BackHandler(enabled = layer == null && appOptions == null && moving == null) {
        scope.launch { listState.animateScrollToItem(0) }
    }

    val homeActions = remember {
        HomeActions(
            openLauncherSettings = { layer = Layer.Settings(SettingsPage.APPEARANCE) },
            openSystemSettings = { activity.start(Intent(Settings.ACTION_SETTINGS)) },
            openWeather = { layer = Layer.Weather() },
            openRates = { layer = Layer.Rates },
            openNews = { layer = Layer.News(it, read = true) },
            launchApp = { app ->
                if (!graph.apps.launch(activity, app)) Toast.makeText(activity, R.string.app_launch_failed, Toast.LENGTH_SHORT).show()
            },
            appOptions = { appOptions = it },
            openProgram = { graph.recommendations.open(activity, it) },
            requestRecPermission = { runCatching { recPermission.launch(RecommendationsRepository.PERMISSION) } },
        )
    }

    CompositionLocalProvider(LocalBackdrop provides backdrop, LocalUiStyle provides settings.uiStyle) {
        Box(Modifier.fillMaxSize().onSizeChanged { backdrop.screen = it }) {
            WallpaperLayer(wallpaper, settings.wallpaper.dim)
            // Full-screen layers sit on the wallpaper alone; the home screen fades out underneath.
            val homeAlpha by animateFloatAsState(if (layer == null) 1f else 0f, tween(220), label = "home")
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                HomeScreen(
                    vm, listState, homeMemory, homeActions,
                    active = layer == null && appOptions == null,
                    moving = moving,
                    onMoveEnd = { moving = null },
                )
            }

            when (val l = layer) {
                null -> Unit
                is Layer.News -> NewsScreen(vm, l.index, l.read, onBack = { layer = null }, onOpenSettings = { layer = Layer.Settings(SettingsPage.NEWS) })
                is Layer.Weather -> WeatherScreen(
                    vm, l.index, onBack = { layer = null },
                    onOpenSettings = { layer = Layer.Settings(SettingsPage.WEATHER) },
                    onOpenTime = { layer = Layer.Clocks(it) },
                )
                is Layer.Clocks -> ClockScreen(
                    vm, l.index, onBack = { layer = null },
                    onOpenSettings = { layer = Layer.Settings(SettingsPage.WEATHER) },
                    onOpenWeather = { layer = Layer.Weather(it) },
                )
                Layer.Rates -> RatesScreen(vm, onBack = { layer = null }, onOpenSettings = { layer = Layer.Settings(SettingsPage.RATES) })
                is Layer.Settings -> SettingsScreen(
                    vm = vm,
                    initialPage = l.page,
                    onBack = { layer = null },
                    actions = SettingsActions(
                        openSystemSettings = { activity.start(Intent(Settings.ACTION_SETTINGS)) },
                        openDreamSettings = { activity.start(Intent(Settings.ACTION_DREAM_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
                        openHomeSettings = {
                            if (!activity.start(Intent(Settings.ACTION_HOME_SETTINGS))) {
                                Toast.makeText(activity, R.string.home_make_default_fallback, Toast.LENGTH_LONG).show()
                            }
                        },
                        previewScreensaver = { layer = Layer.SaverPreview },
                        requestRecPermission = { runCatching { recPermission.launch(RecommendationsRepository.PERMISSION) } },
                        setLanguage = { tag ->
                            AppLocale.store(activity, tag)
                            activity.intent.putExtra(EXTRA_REOPEN_SETTINGS, SettingsPage.LANGUAGE.name)
                            activity.recreate()
                        },
                    ),
                )
                Layer.SaverPreview -> SaverPreview(vm, onBack = { layer = Layer.Settings(SettingsPage.SCREENSAVER) })
            }

            appOptions?.let { app ->
                val favorites by vm.favorites.collectAsState()
                val index = favorites.indexOfFirst { it.key == app.key }
                AppOptionsDialog(
                    app = app,
                    isFavorite = index >= 0,
                    favoriteIndex = index,
                    favoriteCount = favorites.size,
                    onDismiss = { appOptions = null },
                    actions = AppOptionsActions(
                        toggleFavorite = { vm.toggleFavorite(app) },
                        startMove = { moving = app.key },
                        hide = { vm.hide(app) },
                        info = { graph.apps.openAppInfo(activity, app) },
                        uninstall = { graph.apps.uninstall(activity, app) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun WallpaperLayer(model: WallpaperModel, dim: Int) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // One pre-composed opaque picture (wallpaper + gradient + dimming); the previous one stays
        // on screen until the next is ready.
        val picture by produceState<ImageBitmap?>(null, model, dim, width, height) {
            WallpaperComposer.render(context, model, dim, width, height)?.let { value = it }
        }
        Crossfade(targetState = picture, animationSpec = tween(700), label = "wallpaper") { bitmap ->
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            } else {
                Canvas(Modifier.fillMaxSize()) { drawGradientPreset(Gradients.get(0)) }
            }
        }
    }
}

@Composable
private fun SaverPreview(vm: LauncherViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsState()
    val weather by vm.weather.collectAsState()
    val rates = rememberFavoriteRates(vm.rateQuotes, vm::refreshRates, if (settings.screensaver.showRates) settings.rateIds.take(LauncherSettings.HOME_RATES) else emptyList())
    val slides by produceState(emptyList<Slide>()) { value = loadSlides(context.graph, settings) }
    val focus = remember { FocusRequester() }
    ScreenLayer(onBack = onBack, scrim = Color.Black, initialFocus = focus) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { if (it.type == KeyEventType.KeyDown) { onBack(); true } else false },
        ) {
            if (slides.isNotEmpty()) Screensaver(slides, settings, weather, rates)
        }
    }
}

private const val EXTRA_REOPEN_SETTINGS = "io.github.remdee13.pavlovtv.REOPEN_SETTINGS"
