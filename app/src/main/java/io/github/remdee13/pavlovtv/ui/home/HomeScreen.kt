package io.github.remdee13.pavlovtv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AppEntry
import io.github.remdee13.pavlovtv.data.RecProgram
import io.github.remdee13.pavlovtv.data.RecStatus
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.FocusMemory
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassIconButton
import io.github.remdee13.pavlovtv.ui.components.LocalFocusMemory
import io.github.remdee13.pavlovtv.ui.components.SectionTitle
import io.github.remdee13.pavlovtv.ui.components.rememberNow
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.longDate
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.timeText
import io.github.remdee13.pavlovtv.ui.widgets.ClocksWidget
import io.github.remdee13.pavlovtv.ui.widgets.NewsWidget
import io.github.remdee13.pavlovtv.ui.widgets.WeatherWidget
import io.github.remdee13.pavlovtv.ui.widgets.clocksWidgetWidth
import io.github.remdee13.pavlovtv.wallpaper.WallpaperModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

val ScreenPadding = 40.dp
private const val GRID_COLUMNS = 5

class HomeActions(
    val openLauncherSettings: () -> Unit,
    val openSystemSettings: () -> Unit,
    val openWeather: () -> Unit,
    val openClocks: () -> Unit,
    val openNews: (Int) -> Unit,
    val launchApp: (AppEntry) -> Unit,
    val appOptions: (AppEntry) -> Unit,
    val openProgram: (RecProgram) -> Unit,
    val requestRecPermission: () -> Unit,
)

/** Keeps the focused row about a third from the top, like the stock TV home screens. */
@OptIn(ExperimentalFoundationApi::class)
private object PivotSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val initialTarget = containerSize * 0.3f
        val target = if (size <= containerSize && containerSize - initialTarget < size) containerSize - size else initialTarget
        return offset - target
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    listState: LazyListState,
    memory: FocusMemory,
    actions: HomeActions,
    active: Boolean = true,
    moving: String? = null,
    onMoveEnd: () -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val weather by vm.weather.collectAsState()
    val news by vm.news.collectAsState()
    val newsLoading by vm.newsLoading.collectAsState()
    val recRows by vm.recRows.collectAsState()
    val recStatus by vm.recStatus.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()
    val context = LocalContext.current

    var newsIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(news.size) {
        newsIndex = 0
        while (news.size > 1) {
            delay(9_000)
            newsIndex = (newsIndex + 1) % news.size
        }
    }

    BackHandler(enabled = moving != null, onBack = onMoveEnd)

    // Move mode: left/right shift the picked-up favorite along the row, OK or Back puts it down.
    fun moveKey(event: KeyEvent, app: AppEntry): Boolean = when (event.key) {
        Key.DirectionLeft, Key.DirectionRight -> {
            if (event.type == KeyEventType.KeyDown) vm.moveFavorite(app, if (event.key == Key.DirectionLeft) -1 else 1)
            true
        }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
            if (event.type == KeyEventType.KeyUp) onMoveEnd()
            true
        }
        Key.DirectionUp, Key.DirectionDown -> true
        else -> false
    }

    val firstTile = remember { FocusRequester() }
    var initialFocusDone by remember { mutableIntStateOf(0) }
    LaunchedEffect(favorites.isNotEmpty(), apps.isNotEmpty(), active) {
        if (active && initialFocusDone == 0 && (favorites.isNotEmpty() || apps.isNotEmpty())) {
            delay(80)
            runCatching { firstTile.requestFocus() }
            initialFocusDone = 1
            // Focusing the first tile scrolls it to the pivot line; start with the widgets in view instead.
            delay(50)
            listState.scrollToItem(0)
        }
    }

    CompositionLocalProvider(LocalFocusMemory provides memory, LocalBringIntoViewSpec provides PivotSpec) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 26.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "top") { TopBar(settings.use24h, (wallpaper as? WallpaperModel.Image)?.caption.orEmpty(), actions) }

            if (settings.showWeather || settings.showClocks || settings.showNews) {
                item(key = "widgets") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (settings.showWeather) WeatherWidget(weather, actions.openWeather, Modifier.width(250.dp))
                        if (settings.showClocks) {
                            val clocks = settings.effectiveClocks
                            ClocksWidget(clocks, settings.use24h, actions.openClocks, Modifier.width(clocksWidgetWidth(clocks.size)))
                        }
                        if (settings.showNews) {
                            val index = if (news.isEmpty()) 0 else newsIndex % news.size
                            NewsWidget(news.getOrNull(index), index, news.size, newsLoading, { actions.openNews(index) }, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "favorites") {
                Column {
                    SectionTitle(stringResource(R.string.home_favorites), Modifier.padding(horizontal = ScreenPadding))
                    Spacer(Modifier.height(12.dp))
                    if (favorites.isEmpty()) {
                        Text(
                            stringResource(R.string.home_favorites_empty),
                            style = Type.body,
                            color = Palette.TextTertiary,
                            modifier = Modifier.padding(horizontal = ScreenPadding).height(110.dp),
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            itemsIndexed(favorites, key = { _, app -> app.key }) { index, app ->
                                val isMoving = app.key == moving
                                val bring = remember { BringIntoViewRequester() }
                                // Keep the moving tile on screen as it travels along the row.
                                LaunchedEffect(index, isMoving) {
                                    if (isMoving) {
                                        delay(32)
                                        bring.bringIntoView()
                                    }
                                }
                                AppTile(
                                    app = app,
                                    onClick = { actions.launchApp(app) },
                                    onLongClick = { actions.appOptions(app) },
                                    modifier = Modifier
                                        .bringIntoViewRequester(bring)
                                        .then(if (isMoving) Modifier.onPreviewKeyEvent { moveKey(it, app) } else Modifier),
                                    focusRequester = if (index == 0) firstTile else null,
                                    moving = isMoving,
                                )
                            }
                        }
                    }
                }
            }

            if (settings.showRecommendations && recStatus == RecStatus.NO_PERMISSION) {
                item(key = "rec_permission") {
                    Row(Modifier.padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recs_permission_hint), style = Type.body, color = Palette.TextSecondary, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(16.dp))
                        GlassButton(stringResource(R.string.recs_permission_button), actions.requestRecPermission, icon = Icons.Outlined.Lock)
                    }
                }
            }

            recRows.forEach { row ->
                item(key = "rec_${row.id}") {
                    val appName = remember(row.packageName) {
                        runCatching {
                            val pm = context.packageManager
                            pm.getApplicationLabel(pm.getApplicationInfo(row.packageName, 0)).toString()
                        }.getOrDefault("")
                    }
                    val title = when {
                        row.isWatchNext -> stringResource(R.string.recs_watch_next)
                        appName.isNotEmpty() && row.title.isNotEmpty() && !row.title.equals(appName, ignoreCase = true) -> "$appName · ${row.title}"
                        else -> row.title.ifEmpty { appName }
                    }
                    Column {
                        SectionTitle(title, Modifier.padding(horizontal = ScreenPadding))
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(row.programs, key = { it.id }) { program ->
                                RecommendationCard(program, onClick = { actions.openProgram(program) })
                            }
                        }
                    }
                }
            }

            item(key = "all_title") {
                SectionTitle(stringResource(R.string.home_all_apps, apps.size), Modifier.padding(horizontal = ScreenPadding))
            }
            val rows = apps.chunked(GRID_COLUMNS)
            items(rows, key = { row -> "grid_" + row.first().key }) { row ->
                Row(
                    Modifier.padding(horizontal = ScreenPadding).focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    row.forEach { app ->
                        AppTile(app = app, onClick = { actions.launchApp(app) }, onLongClick = { actions.appOptions(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(use24h: Boolean, caption: String, actions: HomeActions) {
    val now by rememberNow(1000L)
    val locale = currentLocale()
    val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(timeText(time, use24h, locale), style = Type.clock)
            Text(longDate(time, locale), style = Type.date, color = Palette.TextSecondary)
        }
        // Credit for the current wallpaper photo, kept up here so it never covers content.
        Text(
            caption,
            style = Type.caption,
            color = Palette.TextTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 32.dp, end = 20.dp),
        )
        GlassIconButton(Icons.Outlined.Tune, stringResource(R.string.action_launcher_settings), actions.openLauncherSettings)
        Spacer(Modifier.width(14.dp))
        GlassIconButton(Icons.Outlined.Settings, stringResource(R.string.action_system_settings), actions.openSystemSettings)
    }
}
