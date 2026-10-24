package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.ui.window.application
import com.github.krpkbhdn.ccmanager.CcManagerApplication
import com.github.krpkbhdn.ccmanager.ui.state.Backend
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost

private const val INTEROP_BLENDING = "compose.interop.blending"

fun main(args: Array<String>) {
    System.setProperty(INTEROP_BLENDING, "true")
    val context = CcManagerApplication.start(args)
    val backend = Backend.from(context)
    TerminalHost.configure(backend.sessions)
    application { AppWindow(backend, onShutdown = context::close) }
}
