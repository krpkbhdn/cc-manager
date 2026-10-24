package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/** Bundled OFL fonts: Inter for UI text, JetBrains Mono for paths, ids and code. */
object AppFonts {
    val ui = FontFamily(
        Font("fonts/Inter-Regular.ttf", FontWeight.Normal, FontStyle.Normal),
        Font("fonts/Inter-Medium.ttf", FontWeight.Medium, FontStyle.Normal),
        Font("fonts/Inter-SemiBold.ttf", FontWeight.SemiBold, FontStyle.Normal),
        Font("fonts/Inter-Bold.ttf", FontWeight.Bold, FontStyle.Normal),
    )
    val mono = FontFamily(
        Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal, FontStyle.Normal),
        Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium, FontStyle.Normal),
        Font("fonts/JetBrainsMono-Bold.ttf", FontWeight.Bold, FontStyle.Normal),
    )
}

private const val UI_FEATURES = "calt, cv11"
private const val TABULAR_FEATURES = "tnum, calt"
private const val MONO_FEATURES = "calt 0"

private fun ui(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f, features: String = UI_FEATURES) = TextStyle(
    fontFamily = AppFonts.ui,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFeatureSettings = features,
)

private fun mono(size: Float, line: Float, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = AppFonts.mono,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    fontFeatureSettings = MONO_FEATURES,
)

/** The type scale: display / title / heading / body / caption / overline, plus mono and tabular variants. */
object Type {
    val display = ui(30f, 36f, FontWeight.SemiBold, -0.9f)
    val title = ui(20f, 26f, FontWeight.SemiBold, -0.5f)
    val subtitle = ui(16f, 22f, FontWeight.SemiBold, -0.3f)
    val heading = ui(14f, 20f, FontWeight.SemiBold, -0.15f)
    val body = ui(13f, 20f, FontWeight.Normal, -0.05f)
    val bodyMedium = ui(13f, 20f, FontWeight.Medium, -0.05f)
    val small = ui(12f, 16f, FontWeight.Normal)
    val smallMedium = ui(12f, 16f, FontWeight.Medium)
    val caption = ui(11f, 14f, FontWeight.Normal, 0.05f)
    val captionMedium = ui(11f, 14f, FontWeight.Medium, 0.05f)
    val overline = ui(10.5f, 14f, FontWeight.SemiBold, 0.6f)
    val numeric = ui(12f, 16f, FontWeight.Medium, 0f, TABULAR_FEATURES)
    val numericCaption = ui(11f, 14f, FontWeight.Medium, 0f, TABULAR_FEATURES)
    val monoBody = mono(12.5f, 20f)
    val monoSmall = mono(11.5f, 16f)
    val monoMedium = mono(12f, 18f, FontWeight.Medium)
}
