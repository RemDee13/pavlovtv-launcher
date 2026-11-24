package io.github.remdee13.pavlovtv.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.TempUnit
import io.github.remdee13.pavlovtv.data.WeatherReport
import io.github.remdee13.pavlovtv.ui.LauncherViewModel
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.ScreenLayer
import io.github.remdee13.pavlovtv.ui.conditionRes
import io.github.remdee13.pavlovtv.ui.currentLocale
import io.github.remdee13.pavlovtv.ui.formatTemp
import io.github.remdee13.pavlovtv.ui.shortDay
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(vm: LauncherViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val bundle by vm.weather.collectAsState()
    val reports = listOfNotNull(bundle?.main) + bundle?.extra.orEmpty()
    val first = remember { FocusRequester() }
    val unit = bundle?.unit ?: TempUnit.CELSIUS
    ScreenLayer(onBack = onBack, initialFocus = first) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weather_title), style = Type.clock.copy(fontSize = 30.sp), modifier = Modifier.weight(1f))
                GlassButton(stringResource(R.string.action_refresh), { vm.refreshWeather() }, icon = Icons.Outlined.Refresh, focusRequester = if (reports.isEmpty()) first else null)
                Spacer(Modifier.width(10.dp))
                GlassButton(stringResource(R.string.weather_cities), onOpenSettings, icon = Icons.Outlined.Tune)
            }
            Spacer(Modifier.height(18.dp))
            if (reports.isEmpty()) {
                Text(stringResource(R.string.weather_unavailable), style = Type.body, color = Palette.TextSecondary)
            }
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(reports, key = { i, r -> "$i${r.place.key}" }) { index, report ->
                    ReportCard(report, unit, onOpenSettings, if (index == 0) first else null)
                }
            }
            Text(stringResource(R.string.weather_attribution), style = Type.tiny, color = Palette.TextTertiary)
        }
    }
}

@Composable
private fun ReportCard(report: WeatherReport, unit: TempUnit, onClick: () -> Unit, focusRequester: FocusRequester?) {
    val locale = currentLocale()
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        focusedScale = 1.015f,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(report.now.code, report.now.isDay, Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.width(220.dp)) {
                Text(report.place.name, style = Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (report.place.country.isNotEmpty()) Text(report.place.country, style = Type.tiny, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(conditionRes(report.now.code)), style = Type.small, color = Palette.TextSecondary)
            }
            Text(formatTemp(report.now.temp), style = Type.bigNumber, modifier = Modifier.width(96.dp))
            Column(Modifier.width(150.dp)) {
                Text(stringResource(R.string.weather_feels_like, formatTemp(report.now.feelsLike)), style = Type.small, color = Palette.TextSecondary)
                Text(stringResource(R.string.weather_humidity, report.now.humidity), style = Type.small, color = Palette.TextSecondary)
                val windUnit = stringResource(if (unit == TempUnit.FAHRENHEIT) R.string.unit_mph else R.string.unit_kmh)
                Text(stringResource(R.string.weather_wind, "${report.now.wind.roundToInt()} $windUnit"), style = Type.small, color = Palette.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            report.days.take(5).forEach { day ->
                Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shortDay(day.date, locale), style = Type.small.copy(fontWeight = FontWeight.Medium))
                    WeatherIcon(day.code, true, Modifier.size(28.dp))
                    Text(formatTemp(day.max), style = Type.small)
                    Text(formatTemp(day.min), style = Type.tiny, color = Palette.TextTertiary)
                }
            }
        }
    }
}
