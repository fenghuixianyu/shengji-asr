package com.shengji.asr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InkBg = Color(0xFF0B0E14)
val InkSurface = Color(0xFF121722)
val InkSurfaceHi = Color(0xFF1A2130)
val InkOutline = Color(0xFF2A3345)
val TextMain = Color(0xFFE8ECF5)
val TextDim = Color(0xFF98A2B8)
val Accent = Color(0xFF7C8CF8)
val AccentSoft = Color(0xFF232B4D)
val Mint = Color(0xFF4FD1C5)
val Amber = Color(0xFFFFB86B)
val Rose = Color(0xFFFF6B81)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B0E14),
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFFD6DBFF),
    secondary = Mint,
    onSecondary = Color(0xFF04201D),
    secondaryContainer = Color(0xFF123A36),
    onSecondaryContainer = Color(0xFFBFF3EC),
    tertiary = Amber,
    onTertiary = Color(0xFF2A1A00),
    background = InkBg,
    onBackground = TextMain,
    surface = InkSurface,
    onSurface = TextMain,
    surfaceVariant = InkSurfaceHi,
    onSurfaceVariant = TextDim,
    outline = InkOutline,
    outlineVariant = Color(0xFF202839),
    error = Rose,
    onError = Color(0xFF2A0008),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun ShengjiAsrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content,
    )
}
