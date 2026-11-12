package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebViewport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebItemScopeTest {
    private val anchor = ElementAnchor("a", "/a", "a", null, null, "", "", "<a>", emptyMap())

    private fun item(id: Long, viewport: WebViewport?) = WebItem(id, 1, 1, id.toInt(), anchor, "c", emptyList(), WebItemStatus.OPEN, null, viewport)

    private val mobile = item(1, WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE))
    private val tablet = item(2, WebViewport(820, 1180, "iPad Air", DeviceCategory.TABLET))
    private val desktop = item(3, WebViewport(1280, 720, null, DeviceCategory.DESKTOP))
    private val old = item(4, null)
    private val all = listOf(mobile, tablet, desktop, old)

    @Test
    fun `responsive scope shows every item`() {
        assertThat(all.visibleIn(null)).containsExactlyElementsOf(all)
    }

    @Test
    fun `a category shows its own items and items without a viewport`() {
        assertThat(all.visibleIn(DeviceCategory.MOBILE)).containsExactly(mobile, old)
        assertThat(all.visibleIn(DeviceCategory.TABLET)).containsExactly(tablet, old)
        assertThat(all.visibleIn(DeviceCategory.DESKTOP)).containsExactly(desktop, old)
    }

    @Test
    fun `label names the device and the size, or only the size`() {
        assertThat(viewportLabel(mobile.viewport())).isEqualTo("iPhone 14 · 390x844")
        assertThat(viewportLabel(desktop.viewport())).isEqualTo("1280x720")
    }
}
