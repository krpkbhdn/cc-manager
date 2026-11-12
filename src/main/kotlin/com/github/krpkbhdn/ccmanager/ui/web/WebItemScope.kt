package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebViewport

/** Whether the item belongs to the device [scope] (null = responsive, everything); items without a recorded viewport belong everywhere. */
fun WebItem.visibleIn(scope: DeviceCategory?): Boolean = scope == null || viewport() == null || viewport().category() == scope

fun List<WebItem>.visibleIn(scope: DeviceCategory?): List<WebItem> = if (scope == null) this else filter { it.visibleIn(scope) }

/** "iPhone 14 · 390x844", or "1280x720" for a free size. */
fun viewportLabel(viewport: WebViewport): String {
    val size = "${viewport.width()}x${viewport.height()}"
    return viewport.deviceName()?.let { "$it · $size" } ?: size
}
