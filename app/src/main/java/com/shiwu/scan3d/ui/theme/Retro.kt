package com.shiwu.scan3d.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- 复古未来主义：炭黑机身 / 米色面板 / 黄铜 / 琥珀 CRT ----
val ShellTop = Color(0xFF2B251C)
val ShellBottom = Color(0xFF14110C)
val Deck = Color(0xFF211C15)
val DeckEdge = Color(0xFF3A3226)
val Plate = Color(0xFFD8C9AB)
val PlateDim = Color(0xFFBFB08F)
val PlateShadow = Color(0xFF9C8E70)
val Brass = Color(0xFFC79A4E)
val BrassLight = Color(0xFFE9C77F)
val BrassDark = Color(0xFF8A6832)
val Amber = Color(0xFFFFB454)
val AmberDim = Color(0xFFB77B32)
val Cream = Color(0xFFF3E7CD)
val Ink = Color(0xFF17130E)
val SignalRed = Color(0xFFC8442E)
val Glass = Color(0xFF070A08)
val GlassEdge = Color(0xFF1D211C)
val GlassTint = Color(0x14FFB454)

private val RetroColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = AmberDim,
    onPrimaryContainer = Cream,
    secondary = Brass,
    onSecondary = Ink,
    secondaryContainer = BrassDark,
    onSecondaryContainer = Cream,
    tertiary = Plate,
    onTertiary = Ink,
    background = ShellBottom,
    onBackground = Cream,
    surface = Deck,
    onSurface = Cream,
    surfaceVariant = Color(0xFF2E2820),
    onSurfaceVariant = PlateDim,
    outline = BrassDark,
    outlineVariant = Color(0xFF3A3226),
    error = SignalRed,
    onError = Cream,
    scrim = Color(0xFF000000),
    inverseSurface = Plate,
    inverseOnSurface = Ink
)

private val RetroType = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = 0.8.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.6.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.3.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 1.6.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp
    )
)

private val RetroShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun RetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RetroColors,
        typography = RetroType,
        shapes = RetroShapes,
        content = content
    )
}
