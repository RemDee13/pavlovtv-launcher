package io.github.remdee13.pavlovtv.wallpaper

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.remdee13.pavlovtv.R
import kotlin.math.max

class GradientPreset(
    @StringRes val name: Int,
    val from: Color,
    val to: Color,
    val blobs: List<Blob>,
)

/** A soft light spot: position and radius are fractions of the screen width. */
class Blob(val color: Color, val x: Float, val y: Float, val radius: Float, val alpha: Float = 0.55f)

/** Built-in wallpapers drawn in code: no image files, no network, look sharp at any size. */
object Gradients {
    val presets = listOf(
        GradientPreset(
            R.string.gradient_aurora, Color(0xFF050B1A), Color(0xFF0B1F3A),
            listOf(Blob(Color(0xFF1DE9B6), 0.18f, 0.32f, 0.55f, 0.45f), Blob(Color(0xFF7C4DFF), 0.82f, 0.18f, 0.5f), Blob(Color(0xFF2979FF), 0.62f, 0.95f, 0.45f, 0.4f)),
        ),
        GradientPreset(
            R.string.gradient_sunset, Color(0xFF1A0B2E), Color(0xFF3D1E4E),
            listOf(Blob(Color(0xFFFF7A45), 0.78f, 0.88f, 0.6f), Blob(Color(0xFFFF4D8D), 0.28f, 0.62f, 0.5f, 0.45f), Blob(Color(0xFFFFC46B), 0.92f, 0.28f, 0.35f, 0.35f)),
        ),
        GradientPreset(
            R.string.gradient_ocean, Color(0xFF021526), Color(0xFF03346E),
            listOf(Blob(Color(0xFF00B4D8), 0.25f, 0.85f, 0.55f), Blob(Color(0xFF0077B6), 0.8f, 0.3f, 0.5f), Blob(Color(0xFF90E0EF), 0.55f, 0.1f, 0.3f, 0.3f)),
        ),
        GradientPreset(
            R.string.gradient_forest, Color(0xFF06120C), Color(0xFF0F2A1D),
            listOf(Blob(Color(0xFF2D6A4F), 0.2f, 0.3f, 0.55f, 0.6f), Blob(Color(0xFF95D5B2), 0.75f, 0.75f, 0.45f, 0.3f), Blob(Color(0xFF1B998B), 0.9f, 0.15f, 0.35f, 0.4f)),
        ),
        GradientPreset(
            R.string.gradient_graphite, Color(0xFF0E0F12), Color(0xFF1C1F26),
            listOf(Blob(Color(0xFF3A4252), 0.25f, 0.25f, 0.6f, 0.6f), Blob(Color(0xFF5A4A3A), 0.85f, 0.85f, 0.5f, 0.45f)),
        ),
        GradientPreset(
            R.string.gradient_midnight, Color(0xFF000000), Color(0xFF0A0A23),
            listOf(Blob(Color(0xFF3F37C9), 0.3f, 0.8f, 0.55f), Blob(Color(0xFF7209B7), 0.8f, 0.25f, 0.45f, 0.45f)),
        ),
        GradientPreset(
            R.string.gradient_sakura, Color(0xFF1F0E1C), Color(0xFF3A1730),
            listOf(Blob(Color(0xFFFFAFCC), 0.75f, 0.3f, 0.5f, 0.4f), Blob(Color(0xFFCDB4DB), 0.2f, 0.75f, 0.5f, 0.4f), Blob(Color(0xFFFF6B9D), 0.5f, 1.0f, 0.4f, 0.35f)),
        ),
        GradientPreset(
            R.string.gradient_desert, Color(0xFF1E120A), Color(0xFF3B2414),
            listOf(Blob(Color(0xFFE9C46A), 0.8f, 0.2f, 0.45f, 0.45f), Blob(Color(0xFFE76F51), 0.25f, 0.85f, 0.55f, 0.5f)),
        ),
    )

    fun get(index: Int): GradientPreset = presets[index.mod(presets.size)]
}

fun DrawScope.drawGradientPreset(preset: GradientPreset) {
    drawRect(Brush.linearGradient(listOf(preset.from, preset.to), start = Offset.Zero, end = Offset(size.width, size.height)))
    val unit = max(size.width, size.height)
    preset.blobs.forEach { blob ->
        val center = Offset(blob.x * size.width, blob.y * size.height)
        val radius = blob.radius * unit
        drawCircle(
            brush = Brush.radialGradient(
                0f to blob.color.copy(alpha = blob.alpha),
                0.55f to blob.color.copy(alpha = blob.alpha * 0.35f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
