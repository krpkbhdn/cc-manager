package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.krpkbhdn.ccmanager.CcManagerApplication
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

fun main(args: Array<String>) {
    val context = CcManagerApplication.start(args)
    application {
        Window(
            onCloseRequest = {
                context.close()
                exitApplication()
            },
            title = "Claude Code Manager",
        ) {
            val theme = if (isSystemInDarkTheme()) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition()
            IntUiTheme(theme = theme, styling = ComponentStyling.default()) {
                HomeView()
            }
        }
    }
}
