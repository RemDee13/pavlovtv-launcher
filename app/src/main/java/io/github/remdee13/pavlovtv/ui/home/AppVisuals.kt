package io.github.remdee13.pavlovtv.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.remdee13.pavlovtv.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppVisual(val banner: ImageBitmap?, val icon: ImageBitmap?, val tint: Color)

/** Decoded TV banners / icons, kept in a small memory cache (1 GB TVs!). */
class AppVisualCache(private val context: Context) {
    private val cache = object : LruCache<String, Pair<AppVisual, Int>>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pair<AppVisual, Int>): Int = value.second
    }

    fun peek(key: String): AppVisual? = cache.get(key)?.first

    suspend fun load(app: AppEntry, bannerWidth: Int, bannerHeight: Int, iconSize: Int): AppVisual = withContext(Dispatchers.IO) {
        peek(app.key)?.let { return@withContext it }
        val pm = context.packageManager
        val info = runCatching { pm.getActivityInfo(app.component, 0) }.getOrNull()
        val bannerDrawable = info?.let { runCatching { it.loadBanner(pm) }.getOrNull() }
            ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()
        val banner = bannerDrawable?.let { runCatching { it.toBitmap(bannerWidth, bannerHeight, Bitmap.Config.ARGB_8888) }.getOrNull() }
        val iconBitmap = if (banner == null) {
            val drawable = info?.let { runCatching { it.loadIcon(pm) }.getOrNull() } ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
            drawable?.let { runCatching { it.toBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888) }.getOrNull() }
        } else {
            null
        }
        val tint = iconBitmap?.let(::tintOf) ?: Color(0xFF2A3446)
        val visual = AppVisual(banner?.asImageBitmap(), iconBitmap?.asImageBitmap(), tint)
        val bytes = (banner?.byteCount ?: 0) + (iconBitmap?.byteCount ?: 0) + 64
        cache.put(app.key, visual to bytes)
        visual
    }

    /** A deep, saturated version of the icon's average colour for the tile background. */
    private fun tintOf(icon: Bitmap): Color {
        val small = Bitmap.createScaledBitmap(icon, 8, 8, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = small.getPixel(x, y)
            val alpha = p ushr 24
            if (alpha < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        if (small !== icon) small.recycle()
        if (n == 0L) return Color(0xFF2A3446)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.1f).coerceIn(0.25f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.5f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

val LocalAppVisuals = staticCompositionLocalOf<AppVisualCache> { error("AppVisualCache not provided") }
