package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

private const val WINDOWS_PREFIX = "Windows"
private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val ENABLED = 1
private const val DISABLED = 0

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntArray, size: Int): Int
}

/** Makes the Windows 11 title bar follow the app theme; anywhere else, or if the call fails, the OS default stays. */
@Composable
fun NativeTitleBarTheme(window: Window, dark: Boolean) {
    LaunchedEffect(window, dark) { applyDarkTitleBar(window, dark) }
}

private fun applyDarkTitleBar(window: Window, dark: Boolean) {
    if (!System.getProperty("os.name").startsWith(WINDOWS_PREFIX)) return
    runCatching {
        val hwnd = Native.getComponentPointer(window) ?: return
        Native.load("dwmapi", DwmApi::class.java).DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, intArrayOf(if (dark) ENABLED else DISABLED), Int.SIZE_BYTES)
    }
}
