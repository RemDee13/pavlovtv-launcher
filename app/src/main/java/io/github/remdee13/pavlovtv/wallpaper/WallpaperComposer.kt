package io.github.remdee13.pavlovtv.wallpaper

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Bakes the home background into one screen-sized picture: the wallpaper, the readability
 * gradient and the user's dimming. Drawing that single opaque bitmap is far cheaper on a
 * TV's GPU than stacking the same layers with transparency on every frame.
 */
object WallpaperComposer {

    suspend fun render(context: Context, model: WallpaperModel, dim: Int, width: Int, height: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            if (width <= 0 || height <= 0) return@withContext null
            val source: ImageBitmap? = (model as? WallpaperModel.Image)?.let { load(context, it.data, width, height) }
            val out = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(width.toFloat(), height.toFloat())) {
                if (source != null) {
                    // Center-crop the picture to the screen.
                    val scale = maxOf(width / source.width.toFloat(), height / source.height.toFloat())
                    val srcW = (width / scale).roundToInt().coerceIn(1, source.width)
                    val srcH = (height / scale).roundToInt().coerceIn(1, source.height)
                    drawImage(
                        source,
                        srcOffset = IntOffset((source.width - srcW) / 2, (source.height - srcH) / 2),
                        srcSize = IntSize(srcW, srcH),
                        dstSize = IntSize(width, height),
                        filterQuality = FilterQuality.Medium,
                    )
                } else {
                    // Built-in gradient, or the fallback when a picture cannot be loaded (offline, bad URL).
                    drawGradientPreset(Gradients.get((model as? WallpaperModel.Gradient)?.index ?: 0))
                }
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.45f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                )
                if (dim > 0) drawRect(Color.Black.copy(alpha = dim / 100f))
            }
            out.asAndroidBitmap().prepareToDraw()
            out
        }

    private suspend fun load(context: Context, data: Any, width: Int, height: Int): ImageBitmap? {
        val request = ImageRequest.Builder(context)
            .data(data)
            .size(width, height)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val drawable = result.drawable
        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                drawable.setBounds(0, 0, it.width, it.height)
                drawable.draw(android.graphics.Canvas(it))
            }
        return bitmap.asImageBitmap()
    }
}
