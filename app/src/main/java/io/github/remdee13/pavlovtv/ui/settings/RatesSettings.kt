package io.github.remdee13.pavlovtv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AssetKind
import io.github.remdee13.pavlovtv.data.LauncherSettings
import io.github.remdee13.pavlovtv.data.RateAsset
import io.github.remdee13.pavlovtv.data.RateCatalog
import io.github.remdee13.pavlovtv.data.homeCurrency
import io.github.remdee13.pavlovtv.data.rateIds
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassDialog
import io.github.remdee13.pavlovtv.ui.components.GlassIconButton
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.rates.changeColor
import io.github.remdee13.pavlovtv.ui.rates.changeText
import io.github.remdee13.pavlovtv.ui.rates.pairLabel
import io.github.remdee13.pavlovtv.ui.rates.pairTitle
import io.github.remdee13.pavlovtv.ui.rates.priceText
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import java.util.Locale

private fun kindOf(id: String): AssetKind =
    if (RateCatalog.isStock(id)) AssetKind.STOCK else RateCatalog.split(id)?.first?.let(RateCatalog::asset)?.kind ?: AssetKind.FIAT

private fun AssetKind.icon(): ImageVector = when (this) {
    AssetKind.FIAT -> Icons.Outlined.CurrencyExchange
    AssetKind.CRYPTO -> Icons.Outlined.CurrencyBitcoin
    AssetKind.METAL -> Icons.Outlined.Diamond
    AssetKind.STOCK -> Icons.Outlined.CandlestickChart
}

private fun AssetKind.title(): Int = when (this) {
    AssetKind.FIAT -> R.string.rates_group_fiat
    AssetKind.CRYPTO -> R.string.rates_group_crypto
    AssetKind.METAL -> R.string.rates_group_metal
    AssetKind.STOCK -> R.string.rates_group_stock
}

private fun LauncherViewModel.setRates(ids: List<String>) = update { it.copy(favoriteRates = ids.distinct().take(LauncherSettings.MAX_RATES)) }

internal fun LazyListScope.ratesPage(vm: LauncherViewModel, s: LauncherSettings, onAdd: () -> Unit, onOptions: (String) -> Unit) {
    val ids = s.rateIds
    item { ToggleRow(stringResource(R.string.home_show_widget), s.showRates, { v -> vm.update { it.copy(showRates = v) } }) }
    item { ToggleRow(stringResource(R.string.saver_show_rates), s.screensaver.showRates, { v -> vm.update { it.copy(screensaver = it.screensaver.copy(showRates = v)) } }) }
    item { PageHeader(stringResource(R.string.rates_favorites, ids.size, LauncherSettings.MAX_RATES)) }
    item { Hint(stringResource(R.string.rates_home_hint, LauncherSettings.HOME_RATES)) }
    items(ids, key = { "rate_$it" }) { id ->
        val locale = currentLocale()
        val quotes by vm.rateQuotes.collectAsState()
        LaunchedEffect(ids) { vm.refreshRates(ids) }
        val day = quotes[id]
        SettingRow(
            title = pairLabel(id, locale),
            subtitle = pairTitle(id, locale),
            onClick = { onOptions(id) },
            icon = kindOf(id).icon(),
        ) {
            if (day != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(priceText(day.last, locale), style = Type.body.copy(fontWeight = FontWeight.SemiBold))
                    Text(changeText(day.change, locale), style = Type.small, color = changeColor(day.change))
                }
            }
        }
    }
    if (ids.size < LauncherSettings.MAX_RATES) {
        item { SettingRow(stringResource(R.string.rates_add), onClick = onAdd, icon = Icons.Outlined.Add) }
    }
    item { Hint(stringResource(R.string.rates_attribution)) }
}

/** Place in the list, the other way round (currencies only) and removal for one favourite. */
@Composable
internal fun RateOptionsDialog(vm: LauncherViewModel, s: LauncherSettings, id: String, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val locale = currentLocale()
    val ids = s.rateIds
    val start = ids.indexOf(id)
    if (start < 0) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(480.dp), initialFocus = first) {
        Text(pairTitle(id, locale), style = Type.title)
        // Moving keeps the dialog open; a local copy so quick presses never read a stale order.
        var at by remember { mutableIntStateOf(start) }
        var order by remember { mutableStateOf(ids) }
        fun move(delta: Int) {
            val list = order.toMutableList()
            val to = (at + delta).coerceIn(0, list.lastIndex)
            if (to == at) return
            list.add(to, list.removeAt(at))
            order = list
            vm.setRates(list)
            at = to
        }
        if (ids.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_move), style = Type.body, color = Palette.TextSecondary)
                GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.app_move), { move(-1) }, focusRequester = first)
                Text("${at + 1} / ${order.size}", style = Type.body)
                GlassIconButton(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.app_move), { move(1) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val pair = RateCatalog.split(id)
            val bothFiat = pair != null && listOf(pair.first, pair.second).all { RateCatalog.asset(it)?.kind == AssetKind.FIAT }
            if (pair != null && bothFiat) {
                val swapped = "${pair.second}/${pair.first}"
                GlassButton(
                    swapped,
                    {
                        vm.setRates(order.map { if (it == id) swapped else it })
                        onDismiss()
                    },
                    icon = Icons.Outlined.SwapHoriz,
                    focusRequester = if (ids.size > 1) null else first,
                )
            }
            GlassButton(
                stringResource(R.string.action_remove),
                {
                    vm.setRates(order - id)
                    onDismiss()
                },
                icon = Icons.Outlined.Delete,
                focusRequester = if (ids.size > 1 || bothFiat) null else first,
            )
        }
    }
}

/**
 * Every currency, coin, metal and share, searchable, priced in a currency picked at the top.
 * Stays open so several can be added at once; any Yahoo Finance ticker works too.
 */
@Composable
internal fun AddRateDialog(vm: LauncherViewModel, s: LauncherSettings, onDismiss: () -> Unit) {
    val locale = currentLocale()
    val field = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val fiat = RateCatalog.fiat.map { it.code }
    var quote by remember { mutableStateOf(homeCurrency().takeIf { it in fiat } ?: "USD") }
    val ids = s.rateIds
    val full = ids.size >= LauncherSettings.MAX_RATES
    val text = query.trim()
    fun idFor(asset: RateAsset): String? = when (asset.kind) {
        AssetKind.STOCK -> RateCatalog.stockId(asset.code)
        else -> if (asset.code == quote) null else "${asset.code}/$quote"
    }
    fun toggle(id: String) {
        val on = id in ids
        if (!on && full) return
        vm.setRates(if (on) ids - id else ids + id)
    }
    val matches = remember(text, locale) { RateCatalog.all.filter { text.isEmpty() || it.matches(text, locale) } }
    // A ticker typed by hand, when the list has no such share.
    val ticker = text.uppercase().takeIf { t -> t.isNotEmpty() && t.length <= 12 && t.all { it.isLetterOrDigit() || it in ".-^=" } }
        ?.takeIf { t -> RateCatalog.all.none { it.code == t } }

    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(680.dp).heightIn(max = 540.dp), initialFocus = field) {
        Text(stringResource(R.string.rates_add), style = Type.title)
        Text(
            if (full) stringResource(R.string.rates_full, LauncherSettings.MAX_RATES) else stringResource(R.string.rates_chosen, ids.size, LauncherSettings.MAX_RATES),
            style = Type.small,
            color = if (full) Palette.Warm else Palette.TextTertiary,
        )
        GlassTextField(query, { query = it }, stringResource(R.string.rates_search_placeholder), focusRequester = field, imeAction = ImeAction.Done)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            item(key = "quote") {
                StepperRow(
                    stringResource(R.string.rates_price_in),
                    quote,
                    onDecrease = { quote = fiat[(fiat.indexOf(quote) - 1 + fiat.size) % fiat.size] },
                    onIncrease = { quote = fiat[(fiat.indexOf(quote) + 1) % fiat.size] },
                    subtitle = RateCatalog.asset(quote)?.name(locale),
                )
            }
            if (ticker != null) {
                item(key = "ticker") {
                    val id = RateCatalog.stockId(ticker)
                    ToggleRow(stringResource(R.string.rates_add_ticker, ticker), id in ids, { toggle(id) }, icon = Icons.Outlined.Add)
                }
            }
            AssetKind.entries.forEach { kind ->
                val inGroup = matches.filter { it.kind == kind && idFor(it) != null }
                if (inGroup.isEmpty()) return@forEach
                item(key = "group_$kind") {
                    Text(stringResource(kind.title()), style = Type.small, color = Palette.TextTertiary, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                }
                items(inGroup, key = { "asset_${kind}_${it.code}" }) { asset ->
                    val id = idFor(asset) ?: return@items
                    ToggleRow(
                        "${asset.name(locale)} · ${asset.code.removePrefix("^")}",
                        id in ids,
                        { toggle(id) },
                        subtitle = pairLabel(id, locale).takeIf { kind != AssetKind.STOCK },
                        icon = kind.icon(),
                    )
                }
            }
        }
    }
}

private fun RateAsset.matches(text: String, locale: Locale): Boolean =
    code.contains(text, ignoreCase = true) ||
        name(locale).contains(text, ignoreCase = true) ||
        name(Locale.ENGLISH).contains(text, ignoreCase = true)
