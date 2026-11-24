package io.github.remdee13.pavlovtv.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.data.AppEntry
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassDialog
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type

class AppOptionsActions(
    val toggleFavorite: () -> Unit,
    val startMove: () -> Unit,
    val hide: () -> Unit,
    val info: () -> Unit,
    val uninstall: () -> Unit,
)

@Composable
fun AppOptionsDialog(
    app: AppEntry,
    isFavorite: Boolean,
    favoriteIndex: Int,
    favoriteCount: Int,
    onDismiss: () -> Unit,
    actions: AppOptionsActions,
) {
    val first = remember { FocusRequester() }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.widthIn(min = 380.dp, max = 520.dp), initialFocus = first) {
        Text(app.label, style = Type.title)
        Text(app.packageName, style = Type.small, color = Palette.TextTertiary)
        GlassButton(
            text = stringResource(if (isFavorite) R.string.app_remove_favorite else R.string.app_add_favorite),
            icon = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            onClick = { actions.toggleFavorite(); onDismiss() },
            focusRequester = first,
        )
        if (isFavorite && favoriteCount > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(stringResource(R.string.app_move), { actions.startMove(); onDismiss() }, icon = Icons.Outlined.SwapHoriz)
                Spacer(Modifier.width(12.dp))
                Text("${favoriteIndex + 1} / $favoriteCount", style = Type.small, color = Palette.TextSecondary)
            }
        }
        GlassButton(stringResource(R.string.app_hide), { actions.hide(); onDismiss() }, icon = Icons.Outlined.VisibilityOff)
        GlassButton(stringResource(R.string.app_info), { actions.info(); onDismiss() }, icon = Icons.Outlined.Info)
        if (!app.isSystem) {
            GlassButton(stringResource(R.string.app_uninstall), { actions.uninstall(); onDismiss() }, icon = Icons.Outlined.Delete)
        }
    }
}
