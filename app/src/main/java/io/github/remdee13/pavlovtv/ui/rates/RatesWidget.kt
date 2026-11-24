package io.github.remdee13.pavlovtv.ui.rates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AssetKind
import io.github.remdee13.pavlovtv.data.RateCatalog
import io.github.remdee13.pavlovtv.data.RateSeries
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.ui.widgets.WidgetHeight
import io.github.remdee13.pavlovtv.ui.widgets.WidgetShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

val RateUp = Color(0xFF5BD18B)
val RateDown = Color(0xFFFF7B7B)

fun changeColor(change: Double): Color = when {
    change > 0.00005 -> RateUp
    change < -0.00005 -> RateDown
    else -> Palette.TextSecondary
}

/** "+0.15%" with a real minus sign. */
fun changeText(change: Double, locale: Locale): String =
    String.format(locale, "%+.2f%%", change * 100).replace('-', '−')

/** Enough digits to see a move: 59 677 for bitcoin, 33.90 for the baht, 1.0942 for the euro. [scale] sets the digits for a difference. */
fun priceText(value: Double, locale: Locale, scale: Double = value): String {
    val format = NumberFormat.getNumberInstance(locale)
    val digits = when {
        abs(scale) >= 1000 -> 0
        abs(scale) >= 10 -> 2
        abs(scale) >= 1 -> 4
        abs(scale) >= 0.01 -> 4
        else -> 6
    }
    format.maximumFractionDigits = digits
    format.minimumFractionDigits = if (digits == 6) 0 else digits
    return format.format(value)
}

/** Short name: "USD/THB", "Gold/USD", "Apple". */
fun pairLabel(id: String, locale: Locale): String {
    if (RateCatalog.isStock(id)) {
        val symbol = id.removePrefix(RateCatalog.STOCK_PREFIX)
        return RateCatalog.asset(symbol)?.name(locale) ?: symbol
    }
    val (base, quote) = RateCatalog.split(id) ?: return id
    val asset = RateCatalog.asset(base)
    val left = if (asset?.kind == AssetKind.METAL) asset.name(locale) else base
    return "$left/$quote"
}

/** Long name: "US dollar → Thai baht", "Tesla · TSLA". */
fun pairTitle(id: String, locale: Locale): String {
    if (RateCatalog.isStock(id)) {
        val symbol = id.removePrefix(RateCatalog.STOCK_PREFIX)
        val name = RateCatalog.asset(symbol)?.name(locale)
        return if (name != null) "$name · $symbol" else symbol
    }
    val (base, quote) = RateCatalog.split(id) ?: return id
    fun name(code: String) = RateCatalog.asset(code)?.name(locale) ?: code
    return "${name(base)} → ${name(quote)}"
}

/**
 * The favourite pairs' last 24 hours, refreshed every few minutes while shown. Shared by the
 * home widget, the screensaver and its preview.
 */
@Composable
fun rememberFavoriteRates(quotes: StateFlow<Map<String, RateSeries>>, refresh: suspend (List<String>) -> Unit, ids: List<String>): List<Pair<String, RateSeries?>> {
    val map by quotes.collectAsState()
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        while (true) {
            refresh(ids)
            delay(5 * 60_000L)
        }
    }
    return ids.map { it to map[it] }
}

val RatesWidgetWidth = 220.dp

/** Three favourite rates with their 24-hour change and a tiny chart. */
@Composable
fun RatesWidget(rates: List<Pair<String, RateSeries?>>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    GlassCard(onClick = onClick, modifier = modifier.height(WidgetHeight), shape = WidgetShape) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rates_title), style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.rates_24h), style = Type.tiny, color = Palette.TextTertiary)
            }
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                rates.forEach { (id, series) -> RateRow(id, series, locale) }
            }
        }
    }
}

@Composable
private fun RateRow(id: String, series: RateSeries?, locale: Locale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pairLabel(id, locale), style = Type.small.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(series?.let { changeText(it.change, locale) } ?: "—", style = Type.tiny, color = series?.let { changeColor(it.change) } ?: Palette.TextTertiary, maxLines = 1)
        }
        if (series != null) {
            Sparkline(series, Modifier.size(42.dp, 20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            series?.let { priceText(it.last, locale) } ?: "…",
            style = Type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}

@Composable
fun Sparkline(series: RateSeries, modifier: Modifier) {
    val color = changeColor(series.change)
    Canvas(modifier) {
        val values = series.values
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun point(i: Int) = Offset(size.width * i / values.lastIndex, (size.height * (1 - (values[i] - lo) / span)).toFloat())
        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1..values.lastIndex) lineTo(point(i).x, point(i).y)
        }
        val fill = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1..values.lastIndex) lineTo(point(i).x, point(i).y)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(line, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
