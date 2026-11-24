package io.github.remdee13.pavlovtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object Palette {
    val Accent = Color(0xFF8AB4FF)
    val Warm = Color(0xFFFFC46B)
    val Danger = Color(0xFFFF8A80)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xD9FFFFFF)
    val TextTertiary = Color(0x99FFFFFF)

    // "Solid" style: the same layout without glass.
    val SolidCard = Color(0xFC161B24)
    val SolidCardFocused = Color(0xFF2A3446)
    val SolidStroke = Color(0x22FFFFFF)
    val SolidSheet = Color(0xFF10141B)
}

object Type {
    private val shadow = Shadow(Color(0x73000000), Offset(0f, 1.5f), 8f)

    val clock = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, shadow = shadow)
    val date = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, shadow = shadow)
    val section = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp, shadow = shadow)
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
    val small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val tiny = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
    val bigNumber = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
    val caption = TextStyle(fontSize = 11.sp, shadow = shadow)
}

@Composable
fun PavlovTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color(0xFF0B1A33),
            surface = Color(0xFF151A22),
            onSurface = Color.White,
            background = Color.Black,
            onBackground = Color.White,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
