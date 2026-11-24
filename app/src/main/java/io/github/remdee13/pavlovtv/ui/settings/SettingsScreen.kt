package io.github.remdee13.pavlovtv.ui.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.remdee13.pavlovtv.BuildConfig
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AppLocale
import io.github.remdee13.pavlovtv.data.ClockStyle
import io.github.remdee13.pavlovtv.data.Http
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.NewsSources
import io.github.remdee13.pavlovtv.data.NewsTopic
import io.github.remdee13.pavlovtv.data.OnlineSource
import io.github.remdee13.pavlovtv.data.RecStatus
import io.github.remdee13.pavlovtv.data.SaverMode
import io.github.remdee13.pavlovtv.data.TempUnit
import io.github.remdee13.pavlovtv.data.UiStyle
import io.github.remdee13.pavlovtv.data.WallpaperKind
import io.github.remdee13.pavlovtv.data.WorldClock
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.GlassDialog
import io.github.remdee13.pavlovtv.ui.components.GlassIconButton
import io.github.remdee13.pavlovtv.ui.components.GlassPanel
import io.github.remdee13.pavlovtv.ui.components.QrCode
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.wallpaper.Gradients
import io.github.remdee13.pavlovtv.wallpaper.WallpaperModel
import io.github.remdee13.pavlovtv.wallpaper.drawGradientPreset
import java.io.File

enum class SettingsPage(@StringRes val title: Int, val icon: ImageVector) {
    APPEARANCE(R.string.settings_appearance, Icons.Outlined.AutoAwesome),
    WALLPAPER(R.string.settings_wallpaper, Icons.Outlined.Wallpaper),
    SCREENSAVER(R.string.settings_screensaver, Icons.Outlined.PlayCircle),
    WEATHER(R.string.settings_weather, Icons.Outlined.Cloud),
    CLOCKS(R.string.settings_clocks, Icons.Outlined.Schedule),
    NEWS(R.string.settings_news, Icons.Outlined.Newspaper),
    HOME(R.string.settings_home, Icons.Outlined.Dashboard),
    APPS(R.string.settings_apps, Icons.Outlined.Apps),
    LANGUAGE(R.string.settings_language, Icons.Outlined.Language),
    ABOUT(R.string.settings_about, Icons.Outlined.Info),
}

class SettingsActions(
    val openSystemSettings: () -> Unit,
    val openDreamSettings: () -> Unit,
    val openHomeSettings: () -> Unit,
    val previewScreensaver: () -> Unit,
    val requestRecPermission: () -> Unit,
    val setLanguage: (String) -> Unit,
)

private sealed interface SettingsDialog {
    data object MainCity : SettingsDialog
    data object ExtraCity : SettingsDialog
    data object AddClock : SettingsDialog
    data class ClockOptions(val index: Int) : SettingsDialog
    data object AddFeed : SettingsDialog
    data object PhoneFeed : SettingsDialog
    data object WallpaperUrl : SettingsDialog
    data object Upload : SettingsDialog
    data object Storage : SettingsDialog
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel, initialPage: SettingsPage, onBack: () -> Unit, actions: SettingsActions) {
    var page by remember { mutableStateOf(initialPage) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val navFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    val contentState = rememberLazyListState()
    val s by vm.settings.collectAsState()
    LaunchedEffect(page) { contentState.scrollToItem(0) }

    ScreenLayer(onBack = onBack, initialFocus = navFocus.getValue(initialPage)) {
        Row(
            Modifier
                .fillMaxSize()
                // While a dialog is open the page behind it must not react to the remote, even if
                // focus has not reached the dialog yet. Back still works and closes the dialog.
                .onPreviewKeyEvent { dialog != null && it.key != Key.Back && it.key != Key.Escape }
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GlassPanel(
                Modifier
                    .width(250.dp)
                    .fillMaxHeight()
                    // Coming back from the page always lands on the page's own item, never on a neighbour
                    // (focusing a neighbour would switch the page).
                    .focusProperties { enter = { navFocus.getValue(page) } }
                    .focusGroup()
                    // Right from the menu opens the page at its first row, not at whatever row is level with the menu item.
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                            runCatching { contentFocus.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
                strength = 1f,
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        Text(stringResource(R.string.settings_title), style = Type.title.copy(fontSize = 22.sp), modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 10.dp))
                    }
                    items(SettingsPage.entries) { p ->
                        NavItem(
                            title = stringResource(p.title),
                            icon = p.icon,
                            selected = p == page,
                            onFocus = { page = p },
                            onClick = { page = p },
                            focusRequester = navFocus.getValue(p),
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        NavItem(stringResource(R.string.action_system_settings), Icons.Outlined.Settings, false, onFocus = {}, onClick = actions.openSystemSettings)
                    }
                }
            }
            GlassPanel(Modifier.weight(1f).fillMaxHeight().focusRequester(contentFocus).focusGroup(), strength = 1f) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = contentState,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "title_${page.name}") {
                        Text(stringResource(page.title), style = Type.title.copy(fontSize = 22.sp), modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                    }
                    when (page) {
                        SettingsPage.APPEARANCE -> appearancePage(vm, s)
                        SettingsPage.WALLPAPER -> wallpaperPage(vm, s) { dialog = it }
                        SettingsPage.SCREENSAVER -> screensaverPage(vm, s, actions)
                        SettingsPage.WEATHER -> weatherPage(vm, s) { dialog = it }
                        SettingsPage.CLOCKS -> clocksPage(vm, s) { dialog = it }
                        SettingsPage.NEWS -> newsPage(vm, s) { dialog = it }
                        SettingsPage.HOME -> homePage(vm, s, actions)
                        SettingsPage.APPS -> appsPage(vm)
                        SettingsPage.LANGUAGE -> languagePage(actions)
                        SettingsPage.ABOUT -> aboutPage()
                    }
                }
            }
        }
    }

    val context = LocalContext.current
    when (val d = dialog) {
        null -> Unit
        SettingsDialog.MainCity -> CitySearchDialog(
            title = stringResource(R.string.weather_pick_city),
            search = vm::searchPlaces,
            allowAuto = true,
            onDismiss = { dialog = null },
            onPick = { place -> vm.update { it.copy(weatherAuto = place == null, weatherPlace = place ?: it.weatherPlace) } },
        )
        SettingsDialog.ExtraCity -> CitySearchDialog(
            title = stringResource(R.string.weather_add_city),
            search = vm::searchPlaces,
            onDismiss = { dialog = null },
            onPick = { place -> if (place != null) vm.update { it.copy(extraPlaces = (it.extraPlaces + place).distinctBy { p -> p.key }.take(LauncherSettings.MAX_EXTRA_PLACES)) } },
        )
        SettingsDialog.AddClock -> CitySearchDialog(
            title = stringResource(R.string.clocks_add),
            search = vm::searchPlaces,
            onDismiss = { dialog = null },
            onPick = { place ->
                if (place != null) vm.update { it.copy(clocks = (it.effectiveClocks + WorldClock(place)).take(LauncherSettings.MAX_CLOCKS)) }
            },
        )
        is SettingsDialog.ClockOptions -> ClockOptionsDialog(vm, s, d.index) { dialog = null }
        SettingsDialog.AddFeed -> AddFeedDialog(vm::addFeed) { dialog = null }
        SettingsDialog.PhoneFeed -> PhoneFeedDialog(vm::addFeed) { dialog = null }
        SettingsDialog.WallpaperUrl -> TextInputDialog(
            title = stringResource(R.string.wallpaper_url),
            initial = s.wallpaper.url.ifEmpty { "https://" },
            placeholder = "https://…/image.jpg",
            keyboardType = KeyboardType.Uri,
            onDismiss = { dialog = null },
            onDone = { url -> if (url.startsWith("http")) vm.update { it.copy(wallpaper = it.wallpaper.copy(kind = WallpaperKind.URL, url = url)) } },
        )
        SettingsDialog.Upload -> UploadDialog(onDismiss = { dialog = null }) { file -> usePhoto(vm, file) }
        SettingsDialog.Storage -> StorageImportDialog(onDismiss = { dialog = null }) { file ->
            usePhoto(vm, file)
            Toast.makeText(context, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun usePhoto(vm: LauncherViewModel, file: File) {
    vm.update { it.copy(wallpaper = it.wallpaper.copy(kind = WallpaperKind.PHOTO, photoPath = file.absolutePath)) }
}

@Composable
private fun NavItem(title: String, icon: ImageVector, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 2.dp),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.03f,
        focusRequester = focusRequester,
        onFocus = { if (it) onFocus() },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(196.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) Palette.Accent else Palette.TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = Type.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = if (selected) Color.White else Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- pages

private fun LazyListScope.appearancePage(vm: LauncherViewModel, s: LauncherSettings) {
    item { PageHeader(stringResource(R.string.appearance_style)) }
    item {
        ChoiceRow(
            title = stringResource(R.string.style_glass),
            subtitle = stringResource(R.string.style_glass_hint),
            selected = s.uiStyle == UiStyle.GLASS,
            onClick = { vm.update { it.copy(uiStyle = UiStyle.GLASS) } },
        )
    }
    item {
        ChoiceRow(
            title = stringResource(R.string.style_solid),
            subtitle = stringResource(R.string.style_solid_hint),
            selected = s.uiStyle == UiStyle.SOLID,
            onClick = { vm.update { it.copy(uiStyle = UiStyle.SOLID) } },
        )
    }
    item { PageHeader(stringResource(R.string.settings_wallpaper)) }
    item {
        StepperRow(
            title = stringResource(R.string.wallpaper_dim),
            value = "${s.wallpaper.dim}%",
            onDecrease = { vm.update { it.copy(wallpaper = it.wallpaper.copy(dim = (it.wallpaper.dim - 10).coerceIn(0, 80))) } },
            onIncrease = { vm.update { it.copy(wallpaper = it.wallpaper.copy(dim = if (it.wallpaper.dim >= 80) 0 else it.wallpaper.dim + 10)) } },
        )
    }
}

private val onlineSources = listOf(
    OnlineSource.BING to (R.string.source_bing to R.string.source_bing_hint),
    OnlineSource.PICSUM to (R.string.source_picsum to R.string.source_picsum_hint),
    OnlineSource.WIKIMEDIA to (R.string.source_wikimedia to R.string.source_wikimedia_hint),
    OnlineSource.ART to (R.string.source_art to R.string.source_art_hint),
    OnlineSource.NASA to (R.string.source_nasa to R.string.source_nasa_hint),
)

private fun LazyListScope.wallpaperPage(vm: LauncherViewModel, s: LauncherSettings, open: (SettingsDialog) -> Unit) {
    val w = s.wallpaper
    item {
        val current by vm.wallpaper.collectAsState()
        val caption = (current as? WallpaperModel.Image)?.caption.orEmpty()
        if (caption.isNotEmpty()) Hint(caption)
    }
    item { PageHeader(stringResource(R.string.wallpaper_source)) }
    item {
        ChoiceRow(stringResource(R.string.source_gradient), w.kind == WallpaperKind.GRADIENT, { vm.update { it.copy(wallpaper = it.wallpaper.copy(kind = WallpaperKind.GRADIENT)) } }, subtitle = stringResource(R.string.source_gradient_hint))
    }
    if (w.kind == WallpaperKind.GRADIENT) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                itemsIndexed(Gradients.presets) { index, preset ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassCard(
                            onClick = { vm.update { it.copy(wallpaper = it.wallpaper.copy(kind = WallpaperKind.GRADIENT, gradient = index)) } },
                            modifier = Modifier.size(128.dp, 72.dp),
                            shape = RoundedCornerShape(14.dp),
                            focusedScale = 1.08f,
                            rimOnTop = true,
                        ) {
                            Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) { drawGradientPreset(preset) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(preset.name), style = Type.small, color = if (index == w.gradient) Palette.Accent else Palette.TextSecondary)
                    }
                }
            }
        }
    }
    onlineSources.forEach { (source, texts) ->
        item(key = "src_$source") {
            ChoiceRow(
                stringResource(texts.first),
                w.kind == WallpaperKind.ONLINE && w.online == source,
                { vm.update { it.copy(wallpaper = it.wallpaper.copy(kind = WallpaperKind.ONLINE, online = source)) } },
                subtitle = stringResource(texts.second),
            )
        }
    }
    if (w.kind == WallpaperKind.ONLINE) {
        item { SettingRow(stringResource(R.string.wallpaper_next), onClick = { vm.nextWallpaper() }, icon = Icons.Outlined.SkipNext) }
    }
    item {
        ChoiceRow(stringResource(R.string.source_photos), w.kind == WallpaperKind.PHOTO, {
            val first = vm.photos.value.firstOrNull()
            vm.update { it.copy(wallpaper = it.wallpaper.copy(kind = WallpaperKind.PHOTO, photoPath = it.wallpaper.photoPath.ifEmpty { first?.absolutePath.orEmpty() })) }
        }, subtitle = stringResource(R.string.source_photos_hint))
    }
    item { SettingRow(stringResource(R.string.wallpaper_from_phone), onClick = { open(SettingsDialog.Upload) }, icon = Icons.Outlined.PhoneAndroid, subtitle = stringResource(R.string.wallpaper_from_phone_hint)) }
    item { SettingRow(stringResource(R.string.wallpaper_from_storage), onClick = { open(SettingsDialog.Storage) }, icon = Icons.Outlined.Usb) }
    item { PhotoStrip(vm, s) }
    item {
        ChoiceRow(stringResource(R.string.source_url), w.kind == WallpaperKind.URL, { open(SettingsDialog.WallpaperUrl) }, subtitle = w.url.ifEmpty { stringResource(R.string.source_url_hint) }, icon = Icons.Outlined.Link)
    }
}

@Composable
private fun PhotoStrip(vm: LauncherViewModel, s: LauncherSettings) {
    val photos by vm.photos.collectAsState()
    val context = LocalContext.current
    if (photos.isEmpty()) return
    Column {
        Hint(stringResource(R.string.wallpaper_photos_hint))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
            items(photos, key = { it.absolutePath }) { file ->
                val selected = s.wallpaper.kind == WallpaperKind.PHOTO && s.wallpaper.photoPath == file.absolutePath
                GlassCard(
                    onClick = { usePhoto(vm, file) },
                    onLongClick = {
                        vm.deletePhoto(file)
                        Toast.makeText(context, R.string.wallpaper_photo_deleted, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(128.dp, 72.dp),
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.08f,
                    rimOnTop = true,
                ) {
                    AsyncImage(file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                    if (selected) {
                        Text("✓", style = Type.title, color = Palette.Accent, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.screensaverPage(vm: LauncherViewModel, s: LauncherSettings, actions: SettingsActions) {
    val c = s.screensaver
    item { Hint(stringResource(R.string.saver_how_to)) }
    item { SettingRow(stringResource(R.string.saver_open_system), onClick = actions.openDreamSettings, icon = Icons.AutoMirrored.Outlined.OpenInNew) }
    item { SettingRow(stringResource(R.string.saver_preview), onClick = actions.previewScreensaver, icon = Icons.Outlined.PlayCircle) }
    item { PageHeader(stringResource(R.string.wallpaper_source)) }
    item {
        ChoiceRow(stringResource(R.string.saver_same_as_wallpaper), c.mode == SaverMode.WALLPAPER, { vm.update { it.copy(screensaver = it.screensaver.copy(mode = SaverMode.WALLPAPER)) } })
    }
    onlineSources.forEach { (source, texts) ->
        item(key = "saver_$source") {
            ChoiceRow(stringResource(texts.first), c.mode == SaverMode.ONLINE && c.online == source, {
                vm.update { it.copy(screensaver = it.screensaver.copy(mode = SaverMode.ONLINE, online = source)) }
            })
        }
    }
    item {
        ChoiceRow(stringResource(R.string.source_photos), c.mode == SaverMode.MY_PHOTOS, { vm.update { it.copy(screensaver = it.screensaver.copy(mode = SaverMode.MY_PHOTOS)) } })
    }
    item { PageHeader(stringResource(R.string.saver_options)) }
    item {
        val steps = listOf(10, 20, 30, 60, 120, 300)
        StepperRow(
            title = stringResource(R.string.saver_interval),
            value = stringResource(R.string.saver_seconds, c.intervalSec),
            onDecrease = { vm.update { it.copy(screensaver = it.screensaver.copy(intervalSec = steps.lastOrNull { v -> v < it.screensaver.intervalSec } ?: steps.first())) } },
            onIncrease = { vm.update { it.copy(screensaver = it.screensaver.copy(intervalSec = steps.firstOrNull { v -> v > it.screensaver.intervalSec } ?: steps.first())) } },
        )
    }
    item { ToggleRow(stringResource(R.string.saver_show_clock), c.showClock, { v -> vm.update { it.copy(screensaver = it.screensaver.copy(showClock = v)) } }) }
    item { ToggleRow(stringResource(R.string.saver_show_weather), c.showWeather, { v -> vm.update { it.copy(screensaver = it.screensaver.copy(showWeather = v)) } }) }
    item { ToggleRow(stringResource(R.string.saver_motion), c.motion, { v -> vm.update { it.copy(screensaver = it.screensaver.copy(motion = v)) } }, subtitle = stringResource(R.string.saver_motion_hint)) }
}

private fun LazyListScope.weatherPage(vm: LauncherViewModel, s: LauncherSettings, open: (SettingsDialog) -> Unit) {
    item { ToggleRow(stringResource(R.string.home_show_widget), s.showWeather, { v -> vm.update { it.copy(showWeather = v) } }) }
    item {
        val weather by vm.weather.collectAsState()
        val city = if (s.weatherAuto || s.weatherPlace == null) {
            stringResource(R.string.weather_auto) + (weather?.main?.place?.name?.let { " · $it" } ?: "")
        } else {
            s.weatherPlace.name
        }
        SettingRow(stringResource(R.string.weather_city), onClick = { open(SettingsDialog.MainCity) }, subtitle = city, icon = Icons.Outlined.Home)
    }
    item { PageHeader(stringResource(R.string.weather_extra_cities)) }
    items(s.extraPlaces, key = { "extra_" + it.key }) { place ->
        SettingRow(
            title = place.name,
            subtitle = stringResource(R.string.tap_to_remove),
            onClick = { vm.update { it.copy(extraPlaces = it.extraPlaces - place) } },
            trailing = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Palette.TextTertiary) },
        )
    }
    if (s.extraPlaces.size < LauncherSettings.MAX_EXTRA_PLACES) {
        item { SettingRow(stringResource(R.string.weather_add_city), onClick = { open(SettingsDialog.ExtraCity) }, icon = Icons.Outlined.Add) }
    }
    item { PageHeader(stringResource(R.string.weather_units)) }
    item { ChoiceRow("°C", s.tempUnit == TempUnit.CELSIUS, { vm.update { it.copy(tempUnit = TempUnit.CELSIUS) } }) }
    item { ChoiceRow("°F", s.tempUnit == TempUnit.FAHRENHEIT, { vm.update { it.copy(tempUnit = TempUnit.FAHRENHEIT) } }) }
    item { Hint(stringResource(R.string.weather_attribution)) }
}

private fun LazyListScope.clocksPage(vm: LauncherViewModel, s: LauncherSettings, open: (SettingsDialog) -> Unit) {
    val clocks = s.effectiveClocks
    item { ToggleRow(stringResource(R.string.home_show_widget), s.showClocks, { v -> vm.update { it.copy(showClocks = v) } }) }
    item { ToggleRow(stringResource(R.string.clocks_24h), s.use24h, { v -> vm.update { it.copy(use24h = v) } }) }
    item { PageHeader(stringResource(R.string.clocks_list, clocks.size, LauncherSettings.MAX_CLOCKS)) }
    itemsIndexed(clocks, key = { i, c -> "clock_$i${c.place.key}" }) { index, clock ->
        SettingRow(
            title = clock.place.name,
            subtitle = stringResource(if (clock.style == ClockStyle.ANALOG) R.string.clocks_analog else R.string.clocks_digital) + " · " + clock.place.timezone,
            onClick = { open(SettingsDialog.ClockOptions(index)) },
            icon = Icons.Outlined.Schedule,
        )
    }
    if (clocks.size < LauncherSettings.MAX_CLOCKS) {
        item { SettingRow(stringResource(R.string.clocks_add), onClick = { open(SettingsDialog.AddClock) }, icon = Icons.Outlined.Add) }
    }
    item { SettingRow(stringResource(R.string.clocks_reset), onClick = { vm.update { it.copy(clocks = null) } }, icon = Icons.Outlined.Refresh) }
}

@Composable
private fun ClockOptionsDialog(vm: LauncherViewModel, s: LauncherSettings, index: Int, onDismiss: () -> Unit) {
    val clocks = s.effectiveClocks
    val first = remember { FocusRequester() }
    val clock = clocks.getOrNull(index)
    if (clock == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
        return
    }
    fun save(list: List<WorldClock>) = vm.update { it.copy(clocks = list) }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(460.dp), initialFocus = first) {
        Text(clock.place.name, style = Type.title)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(stringResource(R.string.clocks_analog), { save(clocks.toMutableList().also { it[index] = clock.copy(style = ClockStyle.ANALOG) }); onDismiss() }, focusRequester = first, accent = clock.style == ClockStyle.ANALOG)
            GlassButton(stringResource(R.string.clocks_digital), { save(clocks.toMutableList().also { it[index] = clock.copy(style = ClockStyle.DIGITAL) }); onDismiss() }, accent = clock.style == ClockStyle.DIGITAL)
        }
        if (clocks.size > 1) {
            // Moving keeps the dialog open, so a clock can travel several places in one go.
            // Work on a local copy: saving is asynchronous, so quick presses must not read a stale order.
            var at by remember { mutableIntStateOf(index) }
            var order by remember { mutableStateOf(clocks) }
            fun move(delta: Int) {
                val list = order.toMutableList()
                val to = (at + delta).coerceIn(0, list.lastIndex)
                if (to == at) return
                list.add(to, list.removeAt(at))
                order = list
                save(list)
                at = to
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_move), style = Type.body, color = Palette.TextSecondary)
                GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.app_move), { move(-1) })
                Text("${at + 1} / ${clocks.size}", style = Type.body)
                GlassIconButton(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.app_move), { move(1) })
            }
        }
        if (clocks.size > 1) {
            GlassButton(stringResource(R.string.action_remove), { save(clocks.filterIndexed { i, _ -> i != index }); onDismiss() }, icon = Icons.Outlined.Delete)
        }
    }
}

private fun LazyListScope.newsPage(vm: LauncherViewModel, s: LauncherSettings, open: (SettingsDialog) -> Unit) {
    val language = vm.language
    val active = s.newsSources ?: NewsSources.defaultsFor(language)
    fun setSources(ids: List<String>, on: Boolean) = vm.update { st ->
        val current = st.newsSources ?: NewsSources.defaultsFor(language)
        st.copy(newsSources = if (on) (current + ids).distinct() else current - ids.toSet())
    }
    item { ToggleRow(stringResource(R.string.home_show_widget), s.showNews, { v -> vm.update { it.copy(showNews = v) } }) }
    item { SettingRow(stringResource(R.string.action_refresh), onClick = { vm.refreshNews() }, icon = Icons.Outlined.Refresh) }

    // Topics: one switch turns on every ready-made feed of that topic.
    item { PageHeader(stringResource(R.string.news_topics)) }
    NewsTopic.entries.forEach { topic ->
        item(key = "topic_$topic") {
            val feeds = NewsSources.forTopic(topic, language)
            val ids = feeds.map { it.id }
            ToggleRow(
                stringResource(topicTitle(topic)),
                ids.any { it in active },
                { on -> setSources(if (on) ids else NewsSources.all.filter { it.topic == topic }.map { it.id }, on) },
                subtitle = feeds.joinToString(" · ") { it.name },
            )
        }
    }

    // Anything else: a site, a YouTube channel, an RSS link.
    item { PageHeader(stringResource(R.string.news_custom)) }
    items(s.customFeeds, key = { "feed_" + it.url }) { feed ->
        SettingRow(feed.name, onClick = { vm.update { it.copy(customFeeds = it.customFeeds - feed) } }, subtitle = feed.url + " · " + stringResource(R.string.tap_to_remove))
    }
    item { SettingRow(stringResource(R.string.news_add_feed), onClick = { open(SettingsDialog.AddFeed) }, icon = Icons.Outlined.Add, subtitle = stringResource(R.string.news_add_feed_hint)) }
    item { SettingRow(stringResource(R.string.news_add_from_phone), onClick = { open(SettingsDialog.PhoneFeed) }, icon = Icons.Outlined.PhoneAndroid, subtitle = stringResource(R.string.news_add_from_phone_hint)) }

    // Every ready-made feed, one by one.
    item { PageHeader(stringResource(R.string.news_catalog)) }
    val catalog = NewsSources.catalog(language)
    NewsTopic.entries.forEach { topic ->
        val sources = catalog.filter { it.topic == topic }
        if (sources.isEmpty()) return@forEach
        item(key = "catalog_$topic") {
            Text(stringResource(topicTitle(topic)), style = Type.small, color = Palette.TextTertiary, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        }
        sources.forEach { source ->
            item(key = "news_${source.id}") {
                ToggleRow(source.name, source.id in active, { on -> setSources(listOf(source.id), on) }, subtitle = runCatching { android.net.Uri.parse(source.url).host }.getOrNull())
            }
        }
    }
}

private fun topicTitle(topic: NewsTopic): Int = when (topic) {
    NewsTopic.AI -> R.string.topic_ai
    NewsTopic.TECH -> R.string.topic_tech
    NewsTopic.BUSINESS -> R.string.topic_business
    NewsTopic.SCIENCE -> R.string.topic_science
    NewsTopic.WORLD -> R.string.topic_world
}

private fun LazyListScope.homePage(vm: LauncherViewModel, s: LauncherSettings, actions: SettingsActions) {
    item { SettingRow(stringResource(R.string.home_make_default), onClick = actions.openHomeSettings, icon = Icons.Outlined.Home, subtitle = stringResource(R.string.home_make_default_hint)) }
    item { PageHeader(stringResource(R.string.home_sections)) }
    item { ToggleRow(stringResource(R.string.settings_weather), s.showWeather, { v -> vm.update { it.copy(showWeather = v) } }) }
    item { ToggleRow(stringResource(R.string.settings_clocks), s.showClocks, { v -> vm.update { it.copy(showClocks = v) } }) }
    item { ToggleRow(stringResource(R.string.settings_news), s.showNews, { v -> vm.update { it.copy(showNews = v) } }) }
    item { ToggleRow(stringResource(R.string.home_phone_apps), s.showPhoneApps, { v -> vm.update { it.copy(showPhoneApps = v) } }, subtitle = stringResource(R.string.home_phone_apps_hint)) }
    item { PageHeader(stringResource(R.string.recs_title)) }
    item { ToggleRow(stringResource(R.string.recs_show), s.showRecommendations, { v -> vm.update { it.copy(showRecommendations = v) } }, subtitle = stringResource(R.string.recs_hint)) }
    item {
        val status by vm.recStatus.collectAsState()
        val text = when (status) {
            RecStatus.OK -> R.string.recs_status_ok
            RecStatus.EMPTY -> R.string.recs_status_empty
            RecStatus.NO_PERMISSION -> R.string.recs_status_permission
            RecStatus.UNSUPPORTED -> R.string.recs_status_unsupported
            RecStatus.UNKNOWN -> R.string.weather_loading
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Hint(stringResource(text))
            if (status == RecStatus.NO_PERMISSION) {
                SettingRow(stringResource(R.string.recs_permission_button), onClick = actions.requestRecPermission, icon = Icons.Outlined.SettingsSuggest, subtitle = stringResource(R.string.recs_adb_hint))
            }
        }
    }
    item {
        ToggleRow(
            stringResource(R.string.recs_only_watch_next),
            s.recOnlyWatchNext,
            { v -> vm.update { it.copy(recOnlyWatchNext = v) } },
            subtitle = stringResource(R.string.recs_only_watch_next_hint),
        )
    }
    if (!s.recOnlyWatchNext) {
        item { PageHeader(stringResource(R.string.recs_rows)) }
        item {
            val rows by vm.allRecRows.collectAsState()
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    val appName = remember(row.packageName) {
                        runCatching { context.packageManager.run { getApplicationLabel(getApplicationInfo(row.packageName, 0)).toString() } }.getOrDefault(row.packageName)
                    }
                    val title = if (row.isWatchNext) stringResource(R.string.recs_watch_next) else "$appName · ${row.title}"
                    ToggleRow(title, row.id !in s.hiddenChannels, { on ->
                        vm.update { st -> st.copy(hiddenChannels = if (on) st.hiddenChannels - row.id else (st.hiddenChannels + row.id).distinct()) }
                    })
                }
            }
        }
    }
}

private fun LazyListScope.appsPage(vm: LauncherViewModel) {
    item { Hint(stringResource(R.string.apps_long_press_hint)) }
    item { SettingRow(stringResource(R.string.apps_reset_favorites), onClick = { vm.update { it.copy(favorites = null) } }, icon = Icons.Outlined.Refresh) }
    item { PageHeader(stringResource(R.string.apps_hidden)) }
    item {
        val hidden by vm.hiddenApps.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hidden.isEmpty()) Hint(stringResource(R.string.apps_hidden_empty))
            hidden.forEach { app ->
                SettingRow(app.label, onClick = { vm.unhide(app) }, subtitle = stringResource(R.string.apps_tap_to_show))
            }
        }
    }
}

private fun LazyListScope.languagePage(actions: SettingsActions) {
    item {
        val context = LocalContext.current
        val current = AppLocale.stored(context)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLocale.supported.forEach { tag ->
                val name = if (tag.isEmpty()) stringResource(R.string.language_system) else AppLocale.nativeNames[tag] ?: tag
                ChoiceRow(name, tag == current, { if (tag != current) actions.setLanguage(tag) })
            }
        }
    }
}

private fun LazyListScope.aboutPage() {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QrCode(Http.REPO_URL, Modifier.size(132.dp))
            Spacer(Modifier.width(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME, style = Type.title)
                Text(stringResource(R.string.about_tagline), style = Type.body, color = Palette.TextSecondary)
                Text(Http.REPO_URL, style = Type.small, color = Palette.Accent)
                Text(stringResource(R.string.about_license), style = Type.small, color = Palette.TextTertiary)
            }
        }
    }
    item { PageHeader(stringResource(R.string.about_sources)) }
    item { Hint(stringResource(R.string.about_sources_text)) }
}
