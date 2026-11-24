package io.github.remdee13.pavlovtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.Place
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.GlassDialog
import io.github.remdee13.pavlovtv.ui.components.TogglePill
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import kotlinx.coroutines.launch

private val RowShape = RoundedCornerShape(18.dp)

@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GlassCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RowShape,
        focusedScale = 1.02f,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        // The card does not pass its minimum height down, so the row keeps it itself to stay centred.
        Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = Palette.TextSecondary)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.body.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = Type.small, color = Palette.TextTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    SettingRow(title = title, subtitle = subtitle, icon = icon, onClick = { onToggle(!checked) }) { TogglePill(checked) }
}

@Composable
fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    SettingRow(title = title, subtitle = subtitle, icon = icon, onClick = onClick) {
        Box(
            Modifier.size(24.dp)
                .background(if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(50))
                .border(1.5.dp, if (selected) Palette.Accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0B1A33))
        }
    }
}

/** Left / right on the remote change the value; OK steps forward. */
@Composable
fun StepperRow(title: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit, subtitle: String? = null) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = onIncrease,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onDecrease(); true }
                Key.DirectionRight -> { onIncrease(); true }
                else -> false
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null, tint = Palette.TextTertiary)
            Text(value, style = Type.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 6.dp))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Palette.TextTertiary)
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.small, color = Palette.TextTertiary, modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun PageHeader(text: String) {
    Text(text, style = Type.section.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextSecondary, modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp))
}

/** Text field that works with the TV on-screen keyboard. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val own = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Kept as TextFieldValue to know where the cursor is; the caller only sees the text.
    var state by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (state.text == value) state else TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = current,
        onValueChange = {
            state = it
            if (it.text != value) onValueChange(it.text)
        },
        singleLine = true,
        textStyle = Type.body.copy(color = Color.White),
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        modifier = modifier
            .focusRequester(focusRequester ?: own)
            .onFocusChanged { focused = it.isFocused }
            // Compose moves focus out of a text field only for a physical D-pad. Phone remote
            // apps and some remotes send "virtual" keys, which would leave the user stuck here.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val atStart = current.selection.collapsed && current.selection.start == 0
                val atEnd = current.selection.collapsed && current.selection.end == current.text.length
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { onAction(); true }
                    Key.DirectionCenter -> { keyboard?.show(); true }
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionLeft -> atStart && focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> atEnd && focusManager.moveFocus(FocusDirection.Right)
                    else -> false
                }
            }
            .fillMaxWidth()
            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.08f), RoundedCornerShape(16.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = Type.body, color = Palette.TextTertiary)
                inner()
            }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(560.dp), initialFocus = field) {
        Text(title, style = Type.title)
        if (hint != null) Text(hint, style = Type.small, color = Palette.TextTertiary)
        GlassTextField(text, { text = it }, placeholder, focusRequester = field, keyboardType = keyboardType, onAction = { onDone(text.trim()); onDismiss() })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(stringResource(R.string.action_save), { onDone(text.trim()); onDismiss() }, icon = Icons.Outlined.Check, accent = true)
            GlassButton(stringResource(R.string.action_cancel), onDismiss)
        }
    }
}

/** Search a city by name (Open-Meteo geocoding). [onPick] gets null for "detect automatically". */
@Composable
fun CitySearchDialog(
    title: String,
    search: suspend (String) -> List<Place>,
    onDismiss: () -> Unit,
    onPick: (Place?) -> Unit,
    allowAuto: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }
    fun doSearch() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = search(query)
            searching = false
            searched = true
        }
    }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(600.dp).heightIn(max = 500.dp), initialFocus = field) {
        Text(title, style = Type.title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                query, { query = it }, stringResource(R.string.city_search_placeholder),
                modifier = Modifier.weight(1f), focusRequester = field, imeAction = ImeAction.Search, onAction = ::doSearch,
            )
            Spacer(Modifier.width(12.dp))
            GlassButton(stringResource(R.string.action_search), ::doSearch, icon = Icons.Outlined.Search, accent = true)
        }
        if (allowAuto) {
            SettingRow(stringResource(R.string.weather_auto), onClick = { onPick(null); onDismiss() }, icon = Icons.Outlined.MyLocation, subtitle = stringResource(R.string.weather_auto_hint))
        }
        when {
            searching -> Hint(stringResource(R.string.city_searching))
            searched && results.isEmpty() -> Hint(stringResource(R.string.city_not_found))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            items(results, key = { it.key }) { place ->
                SettingRow(
                    title = place.name,
                    subtitle = listOf(place.country, place.timezone).filter { it.isNotEmpty() }.joinToString(" · "),
                    onClick = { onPick(place); onDismiss() },
                )
            }
        }
    }
}
