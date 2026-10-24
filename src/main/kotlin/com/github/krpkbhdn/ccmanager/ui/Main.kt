package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.ui.window.application
import com.github.krpkbhdn.ccmanager.CcManagerApplication
import com.github.krpkbhdn.ccmanager.ui.state.Backend

fun main(args: Array<String>) {
    val context = CcManagerApplication.start(args)
    val backend = Backend.from(context)
    application { AppWindow(backend, onShutdown = context::close) }
}
