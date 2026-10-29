package com.shiwu.scan3d.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- 拾物 palette: mint + cream, no blue/purple ----
val Mint900 = Color(0xFF0B3527)
val Mint800 = Color(0xFF12513B)
val Mint700 = Color(0xFF1B6B4F)
val Mint600 = Color(0xFF2A8262)
val Mint500 = Color(0xFF3D9B77)
val Mint400 = Color(0xFF63B694)
val Mint300 = Color(0xFF95D3B5)
val Mint200 = Color(0xFFBEE8D3)
val Mint100 = Color(0xFFDCF3E7)
val Mint50 = Color(0xFFF0FAF4)

val Cream = Color(0xFFFBF8F1)
val CreamDeep = Color(0xFFF3EDE0)
val Sand = Color(0xFFE9DAC0)
val SandDeep = Color(0xFF9A7A4C)
val Clay = Color(0xFFC06E48)
val ClaySoft = Color(0xFFF7DFD1)
val Ink = Color(0xFF16201B)
val InkSoft = Color(0xFF55605A)
val InkFaint = Color(0xFF8A948D)
val Line = Color(0xFFE4DED1)
val White = Color(0xFFFFFFFF)
val Danger = Color(0xFFB3352A)

private val ShiwuColors = lightColorScheme(
    primary = Mint700,
    onPrimary = White,
    primaryContainer = Mint200,
    onPrimaryContainer = Mint900,
    secondary = SandDeep,
    onSecondary = White,
    secondaryContainer = Sand,
    onSecondaryContainer = Color(0xFF3A2C14),
    tertiary = Clay,
    onTertiary = White,
    tertiaryContainer = ClaySoft,
    onTertiaryContainer = Color(0xFF4A1F0E),
    background = Cream,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = InkSoft,
    surfaceTint = Mint700,
    outline = Color(0xFFC3BCAC),
    outlineVariant = Line,
    error = Danger,
    onError = White,
    errorContainer = Color(0xFFFADAD6),
    onErrorContainer = Color(0xFF3F0B05),
    inverseSurface = Mint900,
    inverseOnSurface = Mint50,
    inversePrimary = Mint300,
    scrim = Color(0xFF000000)
)

private val ShiwuTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 46.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1.0).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp
    )
)

private val ShiwuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun ShiwuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShiwuColors,
        typography = ShiwuTypography,
        shapes = ShiwuShapes,
        content = content
    )
}
