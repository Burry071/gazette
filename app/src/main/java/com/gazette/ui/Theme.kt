package com.gazette.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gazette.R

// Tokens are normative in DESIGN.md — values here must match it exactly.
object GazetteColors {
    val Surface = Color(0xFFF6F8F4)
    val Ink = Color(0xFF161A17)
    val InkSoft = Color(0xFF5E6660)
    val Rule = Color(0xFFE0E4DC)
    val Accent = Color(0xFF0E4F34)
    val AccentPress = Color(0xFF0A3B27)
    val Shimmer = Color(0xFFC9CFC6)
}

val SchibstedGrotesk = FontFamily(
    Font(R.font.schibsted_grotesk, FontWeight.Normal),
    Font(R.font.schibsted_grotesk, FontWeight.Medium),
    Font(R.font.schibsted_grotesk, FontWeight.SemiBold),
)

object GazetteType {
    val wordmark = TextStyle(
        fontFamily = SchibstedGrotesk,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.8.sp,
    )
    val total = TextStyle(
        fontFamily = SchibstedGrotesk,
        fontSize = 34.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 37.4.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = "tnum",
    )
    val amount = TextStyle(
        fontFamily = SchibstedGrotesk,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.4.sp,
        fontFeatureSettings = "tnum",
    )
    val label = TextStyle(
        fontFamily = SchibstedGrotesk,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 13.2.sp,
        letterSpacing = 0.08.em,
    )
    val time = TextStyle(
        fontFamily = SchibstedGrotesk,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 13.2.sp,
        letterSpacing = 0.sp,
    )
    val body = TextStyle(
        fontFamily = SchibstedGrotesk,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.85.sp,
    )
}

@Composable
fun GazetteTheme(content: @Composable () -> Unit) {
    // Light-only v1 by decision (DESIGN.md Do's and Don'ts); the scene is daylight.
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = GazetteColors.Surface,
            surface = GazetteColors.Surface,
            onBackground = GazetteColors.Ink,
            onSurface = GazetteColors.Ink,
            primary = GazetteColors.Accent,
            outline = GazetteColors.Rule,
        ),
        typography = Typography(),
        content = content,
    )
}
