package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

/** Interface and text multipliers (1f = unscaled) the user picked in Appearance settings. */
data class Scale(val ui: Float = 1f, val font: Float = 1f) {
    /** An AWT size (window size, minimum size) grown with the interface, which is drawn through [ScaledDensity] but sized by the OS. */
    fun scaled(awtSize: Int): Int = (awtSize * ui).roundToInt()
}

/** Provided once above every window, so separate OS windows can read it too. */
val LocalScale = staticCompositionLocalOf { Scale() }

/**
 * Applies [LocalScale] on top of the window's own density. A separate OS window must call it itself, and the Jewel title bar
 * must stay outside it: Jewel hands the title bar height and insets to the JetBrains Runtime as unscaled values.
 */
@Composable
fun ScaledDensity(content: @Composable () -> Unit) {
    val scale = LocalScale.current
    val base = LocalDensity.current
    val density = Density(base.density * scale.ui, base.fontScale * scale.font)
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
