package io.github.remdee13.pavlovtv.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import io.github.remdee13.pavlovtv.data.UiStyle
import io.github.remdee13.pavlovtv.wallpaper.Gradients
import io.github.remdee13.pavlovtv.wallpaper.WallpaperModel
import io.github.remdee13.pavlovtv.wallpaper.drawGradientPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The wallpaper as seen through frosted glass.
 *
 * Android 11 has no real-time backdrop blur (RenderEffect needs Android 12), so the
 * wallpaper is blurred once on the CPU at low resolution and every glass panel draws
 * the part of it that lies behind the panel. That costs one small texture draw per
 * panel and is cheap even on 1 GB TVs.
 */
@Stable
class Backdrop {
    var image by mutableStateOf<ImageBitmap?>(null)
    var screen by mutableStateOf(IntSize.Zero)
    var dim by mutableFloatStateOf(0f)
}

val LocalBackdrop = staticCompositionLocalOf { Backdrop() }
val LocalUiStyle = staticCompositionLocalOf { UiStyle.GLASS }

/**
 * Background of a card/panel/button. Fills its parent; put it first inside the shape.
 * [strength] 0 = light card, 1 = sheet/dialog (darker, more opaque).
 */
@Composable
fun GlassFill(
    shape: Shape,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    strength: Float = 0f,
) {
    val style = LocalUiStyle.current
    if (style == UiStyle.SOLID) {
        Spacer(
            modifier.drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val fill = when {
                    focused -> Palette.SolidCardFocused
                    strength > 0.5f -> Palette.SolidSheet
                    else -> Palette.SolidCard
                }
                val stroke = if (focused) Color.White else Palette.SolidStroke
                val strokeWidth = if (focused) 2.dp.toPx() else 1.dp.toPx()
                onDrawBehind {
                    drawOutline(outline, fill)
                    drawOutline(outline, stroke, style = Stroke(strokeWidth))
                }
            },
        )
        return
    }

    val backdrop = LocalBackdrop.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    Spacer(
        modifier
            .onGloballyPositioned { coordinates ->
                val p = coordinates.positionInWindow()
                if (p != origin.value) origin.value = p
            }
            .drawWithCache {
                // Everything here is plain shape fills: no clipPath and no per-draw color filter,
                // both of which are slow on the GPUs of cheap TVs.
                val outline = shape.createOutline(size, layoutDirection, this)
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (focused) 0.95f else 0.55f),
                    0.35f to Color.White.copy(alpha = if (focused) 0.35f else 0.10f),
                    0.65f to Color.White.copy(alpha = if (focused) 0.25f else 0.05f),
                    1f to Color.White.copy(alpha = if (focused) 0.75f else 0.28f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                // Top sheen and bottom glow in one gradient.
                val light = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (focused) 0.26f else 0.14f),
                    0.45f to Color.White.copy(alpha = 0.02f),
                    0.7f to Color.Transparent,
                    1f to Color.White.copy(alpha = if (focused) 0.12f else 0.05f),
                )
                val rimWidth = if (focused) 2.dp.toPx() else 1.dp.toPx()
                val milk = if (focused) 0.16f else 0.06f + 0.04f * strength
                val darken = 0.16f + 0.30f * strength
                val image = backdrop.image
                val shader = image?.let { BitmapShader(it.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
                val glass = shader?.let { ShaderBrush(it) }
                val matrix = Matrix()
                onDrawBehind {
                    val screen = backdrop.screen
                    var dim = 0f
                    if (image != null && shader != null && glass != null && screen.width > 0 && screen.height > 0) {
                        // Map the blurred wallpaper so the panel shows what lies behind it, slightly
                        // magnified (a smaller area stretched over the panel reads as a lens).
                        val lens = 0.9f
                        val x0 = origin.value.x + size.width * (1f - lens) / 2f
                        val y0 = origin.value.y + size.height * (1f - lens) / 2f
                        matrix.setScale(screen.width / image.width.toFloat() / lens, screen.height / image.height.toFloat() / lens)
                        matrix.postTranslate(-x0 / lens, -y0 / lens)
                        shader.setLocalMatrix(matrix)
                        drawOutline(outline, glass)
                        dim = backdrop.dim * 0.6f
                    } else {
                        drawOutline(outline, Color(0x66101620))
                    }
                    // Dim, darken and milk layers folded into one translucent grey.
                    val alpha = 1f - (1f - dim) * (1f - darken) * (1f - milk)
                    val grey = if (alpha > 0f) milk / alpha else 0f
                    drawOutline(outline, Color(grey, grey, grey, alpha))
                    drawOutline(outline, light)
                    drawOutline(outline, rim, style = Stroke(rimWidth))
                }
            },
    )
}

/**
 * Edge light drawn ON TOP of image content (app banners, posters), where a
 * background fill would be hidden.
 */
@Composable
fun FocusRim(shape: Shape, focused: Boolean, modifier: Modifier = Modifier) {
    val style = LocalUiStyle.current
    Spacer(
        modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val rim = Brush.linearGradient(
                0f to Color.White.copy(alpha = if (focused) 1f else 0.45f),
                0.4f to Color.White.copy(alpha = if (focused) 0.45f else 0.08f),
                0.7f to Color.White.copy(alpha = if (focused) 0.3f else 0.04f),
                1f to Color.White.copy(alpha = if (focused) 0.85f else 0.25f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.28f),
                0.42f to Color.White.copy(alpha = 0.03f),
                0.43f to Color.Transparent,
            )
            val glassWidth = if (focused) 2.5.dp.toPx() else 1.dp.toPx()
            val solidWidth = 3.dp.toPx()
            onDrawBehind {
                if (style == UiStyle.GLASS) {
                    if (focused) drawOutline(outline, sheen)
                    drawOutline(outline, rim, style = Stroke(glassWidth))
                } else if (focused) {
                    drawOutline(outline, Color.White, style = Stroke(solidWidth))
                }
            }
        },
    )
}

/** Builds the small blurred copy of a wallpaper used by [GlassFill]. */
object BackdropRenderer {
    private const val W = 256
    private const val H = 144

    suspend fun render(context: Context, model: WallpaperModel): ImageBitmap? = withContext(Dispatchers.Default) {
        val source: Bitmap = when (model) {
            is WallpaperModel.Gradient -> {
                val bitmap = ImageBitmap(W, H)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(W.toFloat(), H.toFloat())) {
                    drawGradientPreset(Gradients.get(model.index))
                }
                return@withContext blur(bitmapOf(bitmap))
            }
            is WallpaperModel.Image -> {
                val request = ImageRequest.Builder(context)
                    .data(model.data)
                    .size(W * 2, H * 2)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
                result.drawable.toBitmap()
            }
        }
        blur(centerCrop(source))
    }

    private fun bitmapOf(image: ImageBitmap): Bitmap {
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    private fun centerCrop(src: Bitmap): Bitmap {
        val targetRatio = W / H.toFloat()
        val ratio = src.width / src.height.toFloat()
        val (cw, ch) = if (ratio > targetRatio) (src.height * targetRatio).roundToInt() to src.height else src.width to (src.width / targetRatio).roundToInt()
        val x = max(0, (src.width - cw) / 2)
        val y = max(0, (src.height - ch) / 2)
        val cropped = Bitmap.createBitmap(src, x, y, cw.coerceAtMost(src.width), ch.coerceAtMost(src.height))
        return Bitmap.createScaledBitmap(cropped, W, H, true)
    }

    private fun blur(bitmap: Bitmap): ImageBitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxPass(pixels, tmp, w, h, 7)
            boxPass(tmp, pixels, h, w, 7)
        }
        return saturate(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)).asImageBitmap()
    }

    /** Glass makes colours behind it look richer; done once here, not on every frame. */
    private fun saturate(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1.45f) })
        }
        android.graphics.Canvas(out).drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    /** Horizontal box blur of [src] (w x h) written transposed into [dst] (h x w). */
    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (x in 0 until w) {
                dst[x * h + y] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24) - (out ushr 24)
                red += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
