package io.github.remdee13.pavlovtv.ui.rates

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.ChartRange
import io.github.remdee13.pavlovtv.data.RateSeries
import io.github.remdee13.pavlovtv.data.rateIds
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.GlassPanel
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every favourite pair on a big chart: from the last hour (minute by minute) to five years.
 * Left and Right walk a cursor along the chart to read any moment.
 */
@Composable
fun RatesScreen(vm: LauncherViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val quotes by vm.rateQuotes.collectAsState()
    val ids = settings.rateIds
    val locale = currentLocale()
    var selected by remember { mutableIntStateOf(0) }
    var range by remember { mutableStateOf(ChartRange.D1) }
    var loaded by remember { mutableStateOf<Pair<String, RateSeries?>?>(null) }
    val first = remember { FocusRequester() }
    val id = ids.getOrNull(selected.coerceIn(0, (ids.size - 1).coerceAtLeast(0)))
    val key = "$id|$range"
    // Chips show every favourite's day, not only the three on the home screen.
    LaunchedEffect(ids) { vm.refreshRates(ids) }
    LaunchedEffect(id, range) {
        id ?: return@LaunchedEffect
        delay(200) // focus may only be passing over a chip
        while (true) {
            loaded = key to vm.rateSeries(id, range)
            delay(range.maxAgeMs.coerceAtLeast(60_000L))
        }
    }
    val series = loaded?.second
    val loading = loaded?.first != key
    val ranges = stringArrayResource(R.array.rates_ranges)

    ScreenLayer(onBack = onBack, scrim = Color.Black.copy(alpha = 0.55f), initialFocus = first) {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rates_title), style = Type.clock.copy(fontSize = 28.sp))
                Spacer(Modifier.width(16.dp))
                LazyRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(ids, key = { _, it -> it }) { i, pair ->
                        PairChip(pair, quotes[pair], selected == i, if (i == selected) first else null, locale) { selected = i }
                    }
                }
                Spacer(Modifier.width(12.dp))
                GlassButton(stringResource(R.string.rates_edit), onOpenSettings, icon = Icons.Outlined.Tune, focusRequester = if (ids.isEmpty()) first else null)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
                ChartRange.entries.forEachIndexed { i, r ->
                    RangeChip(ranges.getOrElse(i) { r.name }, r == range) { range = r }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (id == null) return@Column
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassPanel(Modifier.weight(1f).fillMaxHeight(), strength = 0.6f) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(pairTitle(id, locale), style = Type.small, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (series == null) {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(if (loading) R.string.weather_loading else R.string.rates_unavailable), style = Type.body, color = Palette.TextSecondary)
                            return@Column
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(priceText(series.last, locale), style = Type.bigNumber.copy(fontSize = 44.sp))
                            series.unit?.let { Text(" $it", style = Type.title, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 7.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "${changeText(series.change, locale)} · ${ranges.getOrElse(range.ordinal) { "" }}",
                                style = Type.title.copy(fontWeight = FontWeight.Medium),
                                color = changeColor(series.change),
                                modifier = Modifier.padding(bottom = 7.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        PriceChart(series, range, settings.use24h, locale, Modifier.fillMaxWidth().weight(1f))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.rates_chart_hint), style = Type.tiny, color = Palette.TextTertiary)
                    }
                }
                GlassPanel(Modifier.width(250.dp).fillMaxHeight(), strength = 0.6f) {
                    if (series != null) Stats(id, series, settings.use24h, locale)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.rates_attribution), style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PairChip(id: String, day: RateSeries?, selected: Boolean, focusRequester: FocusRequester?, locale: Locale, onFocus: () -> Unit) {
    GlassCard(
        onClick = onFocus,
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        onFocus = { if (it) onFocus() },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pairLabel(id, locale),
                style = Type.small.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
                maxLines = 1,
            )
            if (day != null) {
                Spacer(Modifier.width(8.dp))
                Text(changeText(day.change, locale), style = Type.small, color = changeColor(day.change), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onFocus: () -> Unit) {
    GlassCard(
        onClick = onFocus,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.08f,
        onFocus = { if (it) onFocus() },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = Type.small.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) Palette.Accent else Palette.TextSecondary,
        )
    }
}

/**
 * Price line with a soft fill, three price levels on the right and times along the bottom.
 * Focused, a cursor follows Left and Right (held keys speed it up) and shows that moment.
 */
@Composable
private fun PriceChart(series: RateSeries, range: ChartRange, use24h: Boolean, locale: Locale, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    var focused by remember { mutableStateOf(false) }
    var cursor by remember(series) { mutableIntStateOf(series.values.lastIndex) }
    val zone = ZoneId.systemDefault()
    val color = changeColor(series.change)
    val label = Type.tiny.copy(color = Palette.TextTertiary)
    val tip = Type.small.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
    val pattern = remember(range, use24h, locale) { timePattern(range, use24h, locale) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val n = series.values.size
                val step = (n / 60).coerceAtLeast(1) * if (event.nativeKeyEvent.repeatCount > 4) 4 else 1
                when (event.key) {
                    Key.DirectionLeft -> { cursor = (cursor - step).coerceAtLeast(0); true }
                    Key.DirectionRight -> { cursor = (cursor + step).coerceAtMost(n - 1); true }
                    else -> false
                }
            }
            .focusable()
            .border(if (focused) 1.5.dp else 0.dp, if (focused) Color.White.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(6.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val values = series.values
            val labelWidth = 62.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val topPad = 26.dp.toPx()
            val w = size.width - labelWidth
            val h = size.height - bottomPad
            val lo = series.low
            val hi = series.high
            val span = (hi - lo).takeIf { it > 0 } ?: (hi * 0.001).coerceAtLeast(1e-9)
            fun x(i: Int) = w * i / values.lastIndex.coerceAtLeast(1)
            fun y(v: Double) = topPad + ((hi - v) / span).toFloat() * (h - topPad)
            // Price levels.
            listOf(hi, (hi + lo) / 2, lo).forEach { level ->
                val ly = y(level)
                drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, ly), Offset(w, ly), 1.dp.toPx())
                val text = measurer.measure(priceText(level, locale), label)
                drawText(text, topLeft = Offset(w + 8.dp.toPx(), ly - text.size.height / 2f))
            }
            // Times.
            for (k in 0..3) {
                val i = (values.lastIndex * k / 3f).toInt()
                val text = measurer.measure(format(series.times[i], zone, pattern, locale), label)
                val left = (x(i) - text.size.width / 2f).coerceIn(0f, w - text.size.width)
                drawText(text, topLeft = Offset(left, h + 4.dp.toPx()))
            }
            val line = Path()
            val fill = Path()
            values.indices.forEach { i ->
                val p = Offset(x(i), y(values[i]))
                if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, p.y) } else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
            }
            fill.lineTo(w, h)
            fill.lineTo(0f, h)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), startY = topPad, endY = h))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            // Where the period started, for comparison.
            val startY = y(series.first)
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, startY), Offset(w, startY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            // Cursor.
            val c = cursor.coerceIn(0, values.lastIndex)
            val cp = Offset(x(c), y(values[c]))
            if (focused) drawLine(Color.White.copy(alpha = 0.4f), Offset(cp.x, topPad - 6.dp.toPx()), Offset(cp.x, h), 1.dp.toPx())
            drawCircle(color.copy(alpha = 0.3f), 8.dp.toPx(), cp)
            drawCircle(Color.White, 4.dp.toPx(), cp)
            if (focused) {
                val text = measurer.measure("${format(series.times[c], zone, pattern, locale)}  ${priceText(values[c], locale)}", tip)
                val pad = 6.dp.toPx()
                val boxW = text.size.width + pad * 2
                val left = (cp.x - boxW / 2).coerceIn(0f, size.width - boxW)
                drawRoundRect(Color(0xE61A2233), Offset(left, 0f), Size(boxW, text.size.height + pad), CornerRadius(8.dp.toPx()))
                drawText(text, topLeft = Offset(left + pad, pad / 2))
            }
        }
    }
}

@Composable
private fun Stats(id: String, series: RateSeries, use24h: Boolean, locale: Locale) {
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatLine(stringResource(R.string.rates_open), priceText(series.first, locale))
        StatLine(stringResource(R.string.rates_high), priceText(series.high, locale))
        StatLine(stringResource(R.string.rates_low), priceText(series.low, locale))
        StatLine(
            stringResource(R.string.rates_change),
            (if (series.last >= series.first) "+" else "−") + priceText(kotlin.math.abs(series.last - series.first), locale, series.last),
            changeColor(series.change),
        )
        // The other way round: how much of the base one unit of the quote buys.
        pairLabel(id, locale).split('/').takeIf { it.size == 2 && series.last > 0 }?.let { (base, quote) ->
            StatLine(stringResource(R.string.rates_inverse), "1 $quote = ${priceText(1 / series.last, locale)} $base")
        }
        Spacer(Modifier.weight(1f))
        val updated = format(series.fetchedAt, zone, if (use24h) "HH:mm" else "h:mm a", locale)
        Text(stringResource(R.string.rates_updated, updated), style = Type.tiny, color = Palette.TextTertiary)
        Text(series.source, style = Type.tiny, color = Palette.TextTertiary)
    }
}

@Composable
private fun StatLine(label: String, value: String, color: Color = Palette.TextPrimary) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(label, style = Type.tiny, color = Palette.TextTertiary)
        Text(value, style = Type.title.copy(fontSize = 16.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun timePattern(range: ChartRange, use24h: Boolean, locale: Locale): String = when (range) {
    ChartRange.H1, ChartRange.D1 -> if (use24h) "HH:mm" else "h:mm a"
    ChartRange.W1 -> DateFormat.getBestDateTimePattern(locale, if (use24h) "EEEHHmm" else "EEEhmma")
    ChartRange.M1 -> DateFormat.getBestDateTimePattern(locale, "MMMd")
    ChartRange.Y1, ChartRange.Y5 -> DateFormat.getBestDateTimePattern(locale, "MMMyyyy")
}

private fun format(ms: Long, zone: ZoneId, pattern: String, locale: Locale): String =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).format(DateTimeFormatter.ofPattern(pattern, locale))
