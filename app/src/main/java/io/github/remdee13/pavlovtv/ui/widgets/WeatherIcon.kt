package io.github.remdee13.pavlovtv.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.github.remdee13.pavlovtv.data.WeatherGlyph
import io.github.remdee13.pavlovtv.data.glyphFor
import kotlin.math.cos
import kotlin.math.sin

private val SunCore = listOf(Color(0xFFFFE08A), Color(0xFFFFB347))
private val MoonColor = Color(0xFFE8ECF8)
private val CloudLight = listOf(Color(0xFFFFFFFF), Color(0xFFD6DEEA))
private val CloudDark = listOf(Color(0xFFB8C2D3), Color(0xFF7D889C))
private val Drop = Color(0xFF7CC4FF)
private val Bolt = Color(0xFFFFD54F)

/** Weather pictogram drawn in code, so it stays crisp at any size and costs no assets. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    val glyph = glyphFor(code)
    Canvas(modifier) {
        val s = size.minDimension
        translate((size.width - s) / 2f, (size.height - s) / 2f) {
            when (glyph) {
                WeatherGlyph.CLEAR -> if (isDay) sun(Offset(s * 0.5f, s * 0.5f), s * 0.24f, s) else moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f)
                WeatherGlyph.PARTLY -> {
                    if (isDay) sun(Offset(s * 0.64f, s * 0.36f), s * 0.17f, s * 0.72f) else moon(Offset(s * 0.64f, s * 0.36f), s * 0.2f)
                    cloud(Offset(s * 0.06f, s * 0.38f), s * 0.78f, light = true)
                }
                WeatherGlyph.CLOUDY -> {
                    cloud(Offset(s * 0.22f, s * 0.2f), s * 0.66f, light = false)
                    cloud(Offset(s * 0.04f, s * 0.34f), s * 0.8f, light = true)
                }
                WeatherGlyph.FOG -> {
                    cloud(Offset(s * 0.1f, s * 0.14f), s * 0.8f, light = true)
                    for (i in 0..2) {
                        val y = s * (0.7f + i * 0.1f)
                        drawLine(Color.White.copy(alpha = 0.8f - i * 0.2f), Offset(s * (0.12f + i * 0.06f), y), Offset(s * (0.88f - i * 0.04f), y), s * 0.045f, StrokeCap.Round)
                    }
                }
                WeatherGlyph.DRIZZLE -> {
                    cloud(Offset(s * 0.08f, s * 0.12f), s * 0.84f, light = true)
                    drops(s, count = 3, length = 0.08f)
                }
                WeatherGlyph.RAIN -> {
                    cloud(Offset(s * 0.08f, s * 0.1f), s * 0.84f, light = false)
                    drops(s, count = 4, length = 0.15f)
                }
                WeatherGlyph.SLEET -> {
                    cloud(Offset(s * 0.08f, s * 0.1f), s * 0.84f, light = false)
                    drops(s, count = 2, length = 0.12f)
                    flakes(s, listOf(0.38f, 0.7f))
                }
                WeatherGlyph.SNOW -> {
                    cloud(Offset(s * 0.08f, s * 0.1f), s * 0.84f, light = true)
                    flakes(s, listOf(0.28f, 0.5f, 0.72f))
                }
                WeatherGlyph.THUNDER -> {
                    cloud(Offset(s * 0.08f, s * 0.08f), s * 0.84f, light = false)
                    bolt(s)
                }
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, box: Float) {
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val inner = radius * 1.35f
        val outer = radius * 1.85f
        drawLine(
            color = SunCore[1].copy(alpha = 0.9f),
            start = center + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
            end = center + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
            strokeWidth = box * 0.055f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(Brush.radialGradient(SunCore, center = center - Offset(radius * 0.3f, radius * 0.3f), radius = radius * 1.6f), radius, center)
}

private fun DrawScope.moon(center: Offset, radius: Float) {
    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center + Offset(radius * 0.55f, -radius * 0.35f), radius * 0.85f)) }
    val crescent = Path().apply { op(full, bite, PathOperation.Difference) }
    drawPath(crescent, Brush.linearGradient(listOf(Color.White, MoonColor), start = center - Offset(radius, radius), end = center + Offset(radius, radius)))
}

/** Cloud whose bounding box starts at [topLeft] and is [width] wide (height ~ 0.55 width). */
private fun DrawScope.cloud(topLeft: Offset, width: Float, light: Boolean) {
    val colors = if (light) CloudLight else CloudDark
    val h = width * 0.55f
    val brush = Brush.verticalGradient(colors, startY = topLeft.y, endY = topLeft.y + h)
    val path = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(topLeft.x + width * 0.42f, topLeft.y + h * 0.42f), width * 0.27f))
        addOval(androidx.compose.ui.geometry.Rect(Offset(topLeft.x + width * 0.66f, topLeft.y + h * 0.62f), width * 0.2f))
        addOval(androidx.compose.ui.geometry.Rect(Offset(topLeft.x + width * 0.24f, topLeft.y + h * 0.66f), width * 0.18f))
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = topLeft.x + width * 0.08f,
                top = topLeft.y + h * 0.56f,
                right = topLeft.x + width * 0.92f,
                bottom = topLeft.y + h,
                radiusX = h * 0.22f,
                radiusY = h * 0.22f,
            ),
        )
    }
    // One path, one fill: overlapping parts are not painted twice.
    drawPath(path, brush)
}

private fun DrawScope.drops(s: Float, count: Int, length: Float) {
    val startY = s * 0.68f
    val spacing = 0.6f / count
    for (i in 0 until count) {
        val x = s * (0.24f + i * spacing + spacing * 0.3f)
        drawLine(Drop, Offset(x, startY + (i % 2) * s * 0.05f), Offset(x - s * 0.05f, startY + (i % 2) * s * 0.05f + s * length), s * 0.05f, StrokeCap.Round)
    }
}

private fun DrawScope.flakes(s: Float, xs: List<Float>) {
    xs.forEachIndexed { i, fx ->
        val c = Offset(s * fx, s * (0.76f + (i % 2) * 0.08f))
        val r = s * 0.06f
        rotate(i * 15f, c) {
            for (k in 0 until 3) {
                val a = Math.toRadians(k * 60.0)
                val d = Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                drawLine(Color.White, c - d, c + d, s * 0.028f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.bolt(s: Float) {
    val path = Path().apply {
        moveTo(s * 0.52f, s * 0.56f)
        lineTo(s * 0.38f, s * 0.78f)
        lineTo(s * 0.5f, s * 0.78f)
        lineTo(s * 0.42f, s * 0.96f)
        lineTo(s * 0.64f, s * 0.7f)
        lineTo(s * 0.52f, s * 0.7f)
        lineTo(s * 0.6f, s * 0.56f)
        close()
    }
    drawPath(path, Bolt)
}
