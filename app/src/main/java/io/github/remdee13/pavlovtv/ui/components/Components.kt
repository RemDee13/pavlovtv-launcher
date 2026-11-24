package io.github.remdee13.pavlovtv.ui.components

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.remdee13.pavlovtv.ui.theme.FocusRim
import io.github.remdee13.pavlovtv.ui.theme.GlassFill
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import kotlinx.coroutines.delay

/** Remembers which element had focus so it can get it back after an overlay closes. */
class FocusMemory {
    var last: FocusRequester? = null
    private var pinned: FocusRequester? = null

    fun restore(): Boolean = runCatching { last?.requestFocus() }.isSuccess && last != null

    /** Remember the current target while an overlay is open: closing it moves focus around before we can restore. */
    fun pin() {
        if (pinned == null) pinned = last
    }

    fun restorePinned(): Boolean {
        pinned?.let { last = it }
        pinned = null
        return restore()
    }
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/**
 * The one focusable building block. Glass or solid depending on the UI style,
 * scales up a little when focused.
 */
@Composable
fun GlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    focusedScale: Float = 1.04f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    focusRequester: FocusRequester? = null,
    /** For cards whose content is an opaque image: draw the glass edge over it. */
    rimOnTop: Boolean = false,
    onFocus: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester
    val memory = LocalFocusMemory.current
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged {
                if (focused != it.isFocused) onFocus?.invoke(it.isFocused)
                focused = it.isFocused
                if (it.isFocused) memory?.last = requester
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color.White,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
    ) {
        // Opaque-image cards hide the glass anyway; skipping it saves a full-size GPU pass per tile.
        if (!rimOnTop) GlassFill(shape = shape, modifier = Modifier.matchParentSize(), focused = focused)
        Box(Modifier.padding(contentPadding)) { content(focused) }
        if (rimOnTop) FocusRim(shape = shape, focused = focused, modifier = Modifier.matchParentSize())
    }
}

/** Static (not focusable) glass panel. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    strength: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        GlassFill(shape = shape, modifier = Modifier.matchParentSize(), strength = strength)
        content()
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    accent: Boolean = false,
) {
    GlassCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (accent) Palette.Accent else Color.White)
            Text(text, style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (accent) Palette.Accent else Color.White)
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    focusRequester: FocusRequester? = null,
) {
    GlassCard(onClick = onClick, modifier = modifier.size(size), shape = CircleShape, focusedScale = 1.1f, focusRequester = focusRequester) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** On/off pill used in settings rows. */
@Composable
fun TogglePill(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(42.dp)
            .height(24.dp)
            .background(if (checked) Palette.Accent else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(18.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.section, color = Palette.TextSecondary, modifier = modifier)
}

/**
 * Full-screen layer above the home screen: traps D-pad focus inside itself,
 * takes initial focus and handles Back.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenLayer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Color = Color.Black.copy(alpha = 0.4f),
    initialFocus: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val group = remember { FocusRequester() }
    val memory = remember { FocusMemory() }
    // A layer opened from inside another layer (a dialog inside settings) hands focus back to the row
    // that opened it. Posted, because the focus tree is still changing while this layer is disposed.
    // The layer below on screen, not the composition parent: settings dialogs are composed next
    // to the settings layer, yet focus must go back to the settings row that opened them.
    val parent = LocalFocusMemory.current.let { local -> remember { openLayers.lastOrNull()?.memory ?: local } }
    val returnTo = remember { parent?.last }
    val token = remember { OpenLayer(memory) }
    DisposableEffect(Unit) {
        openLayers += token
        onDispose {
            openLayers -= token
            if (parent != null && returnTo != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    parent.last = returnTo
                    parent.restore()
                }, 60)
            }
        }
    }
    BackHandler(onBack = onBack)
    var hasFocus by remember { mutableStateOf(false) }
    // A layer opened by a long press appears while OK is still held: the key repeats and the
    // release would land on the layer's first button (a long press on a favourite removed it
    // right away). OK only works here once it has been pressed afresh inside this layer.
    var armed by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalFocusMemory provides memory) {
        Box(
            modifier
                .fillMaxSize()
                .background(scrim)
                .onFocusChanged { hasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.key !in ConfirmKeys) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                    !armed
                }
                .focusRequester(group)
                // Only the top layer keeps focus in. A layer under a dialog must let it go,
                // or the dialog's request for focus is cancelled and the remote is stuck.
                .focusProperties { exit = { if (openLayers.lastOrNull() === token) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup(),
            content = content,
        )
    }
    // Pull focus into the layer. On a slow TV the first frames may come before the layer's
    // content is placed, and a request then silently does nothing, leaving focus on the
    // screen behind. So keep asking, frame by frame, until focus is really inside.
    LaunchedEffect(Unit) {
        repeat(90) {
            withFrameNanos { }
            if (hasFocus) return@LaunchedEffect
            runCatching { (initialFocus ?: group).requestFocus() }
            if (it > 20 && !hasFocus) runCatching { group.requestFocus() }
        }
    }
}

private class OpenLayer(val memory: FocusMemory)

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** [ScreenLayer]s currently on screen, oldest first. */
private val openLayers = mutableStateListOf<OpenLayer>()

/** Centered dialog card on top of a [ScreenLayer]. */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenLayer(onBack = onDismiss, scrim = Color.Black.copy(alpha = 0.55f), initialFocus = initialFocus) {
        GlassPanel(modifier.align(Alignment.Center), shape = RoundedCornerShape(30.dp), strength = 1f) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    Canvas(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** Current time in millis, ticking on the [periodMs] boundary. */
@Composable
fun rememberNow(periodMs: Long = 1000L): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        val now = System.currentTimeMillis()
        value = now
        delay(periodMs - now % periodMs + 5)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
