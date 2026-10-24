package com.github.krpkbhdn.ccmanager.ui.chrome

import com.jetbrains.JBR
import org.slf4j.LoggerFactory

private const val NATIVE_FRAME_PROPERTY = "ccm.nativeFrame"

/** Whether the running JVM is JetBrainsRuntime with custom window decorations; logs once when it is not. `-Dccm.nativeFrame=true` forces the OS frame. */
object WindowDecorationSupport {
    private val log = LoggerFactory.getLogger(WindowDecorationSupport::class.java)

    val available: Boolean = (!java.lang.Boolean.getBoolean(NATIVE_FRAME_PROPERTY) && runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)).also {
        if (!it) log.warn("Custom window decorations are unavailable (JetBrainsRuntime needed, running {}); using the native window frame", System.getProperty("java.vendor"))
    }
}
