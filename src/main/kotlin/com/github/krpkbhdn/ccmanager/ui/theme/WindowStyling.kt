package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.defaults
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.styling.DecoratedWindowColors
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

val titleBarHeight = 40.dp
private val windowButtonSize = DpSize(46.dp, titleBarHeight)
private const val CLOSE_PRESSED_ALPHA = 0.8f

/** The window paints these borders over an opaque base, so a translucent token would wash out against the OS background. */
private fun AppColors.hairline() = border.compositeOver(sidebar)

/** Jewel title bar and window-border styling painted from our palette, so the native window controls match the app. */
@Composable
fun ComponentStyling.withAppWindow(palette: AppColors): ComponentStyling {
    val metrics = TitleBarMetrics.defaults(height = titleBarHeight, titlePaneButtonSize = windowButtonSize)
    val colors = titleBarColors(palette)
    val titleBar = if (palette.dark) TitleBarStyle.dark(colors = colors, metrics = metrics) else TitleBarStyle.light(colors = colors, metrics = metrics)
    val frame = DecoratedWindowColors.light(palette.hairline(), palette.hairline())
    val window = if (palette.dark) DecoratedWindowStyle.dark(frame) else DecoratedWindowStyle.light(frame)
    return decoratedWindow(window, titleBar)
}

@Composable
private fun titleBarColors(palette: AppColors): TitleBarColors {
    val closePressed = palette.danger.copy(alpha = CLOSE_PRESSED_ALPHA)
    return if (palette.dark) {
        TitleBarColors.dark(
            backgroundColor = palette.sidebar, inactiveBackground = palette.sidebar, contentColor = palette.textSecondary, borderColor = palette.hairline(),
            fullscreenControlButtonsBackground = palette.sidebar, titlePaneButtonHoveredBackground = palette.hoverOverlay, titlePaneButtonPressedBackground = palette.pressOverlay,
            titlePaneCloseButtonHoveredBackground = palette.danger, titlePaneCloseButtonPressedBackground = closePressed,
            iconButtonHoveredBackground = palette.hoverOverlay, iconButtonPressedBackground = palette.pressOverlay,
            dropdownPressedBackground = palette.pressOverlay, dropdownHoveredBackground = palette.hoverOverlay,
        )
    } else {
        TitleBarColors.light(
            backgroundColor = palette.sidebar, inactiveBackground = palette.sidebar, contentColor = palette.textSecondary, borderColor = palette.hairline(),
            fullscreenControlButtonsBackground = palette.sidebar, titlePaneButtonHoveredBackground = palette.hoverOverlay, titlePaneButtonPressedBackground = palette.pressOverlay,
            titlePaneCloseButtonHoveredBackground = palette.danger, titlePaneCloseButtonPressedBackground = closePressed,
            iconButtonHoveredBackground = palette.hoverOverlay, iconButtonPressedBackground = palette.pressOverlay,
            dropdownPressedBackground = palette.pressOverlay, dropdownHoveredBackground = palette.hoverOverlay,
        )
    }
}
