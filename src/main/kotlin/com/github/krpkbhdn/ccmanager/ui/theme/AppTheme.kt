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

/** Jewel theme (with Inter as its default text style) plus our own tokens; [dark] picks the palette. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) Palette.dark else Palette.light
    val body = Type.body.copy(color = palette.textPrimary)
    val definition = if (dark) {
        JewelTheme.darkThemeDefinition(defaultTextStyle = body, editorTextStyle = Type.monoBody, consoleTextStyle = Type.monoBody)
    } else {
        JewelTheme.lightThemeDefinition(defaultTextStyle = body, editorTextStyle = Type.monoBody, consoleTextStyle = Type.monoBody)
    }
    IntUiTheme(theme = definition, styling = ComponentStyling.default()) {
        CompositionLocalProvider(LocalAppColors provides palette, content = content)
    }
}
