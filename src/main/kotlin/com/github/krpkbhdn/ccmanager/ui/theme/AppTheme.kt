package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

/** Jewel theme plus our own tokens, following the system theme unless [dark] forces one. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val definition = if (dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition()
    IntUiTheme(theme = definition, styling = ComponentStyling.default()) {
        CompositionLocalProvider(LocalAppColors provides if (dark) Palette.dark else Palette.light, content = content)
    }
}
