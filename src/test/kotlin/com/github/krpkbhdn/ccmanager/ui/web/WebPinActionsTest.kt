package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebViewport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebPinActionsTest {
    private val actions = WebPinActions()
    private val host = FakePageHost()
    private val view = WebView(1, host)
    private val item = WebItem(7, 1, 9, 1, ElementAnchor("a", "/a", "a", null, null, "", "", "<a>", emptyMap()), "c", emptyList(), WebItemStatus.OPEN, null, null)

    @Test
    fun `hover shows the popover and leave hides it`() {
        view.pageItems = listOf(item)
        actions.onHover(view, InspectorMessage.AnnotationHover("7", Rect(1.0, 2.0, 3.0, 4.0)))
        assertThat(view.itemPopover).isEqualTo(ItemPopoverState("7", Rect(1.0, 2.0, 3.0, 4.0)))
        actions.onLeave(view)
        assertThat(view.itemPopover).isNull()
    }

    @Test
    fun `a forged hover for an unknown id shows nothing`() {
        view.pageItems = listOf(item)
        actions.onHover(view, InspectorMessage.AnnotationHover("999", null))
        assertThat(view.itemPopover).isNull()
    }

    @Test
    fun `a hover on an item hidden by the device scope shows nothing`() {
        view.pageItems = listOf(WebItem(7, 1, 9, 1, item.anchor(), "c", emptyList(), WebItemStatus.OPEN, null, WebViewport(1280, 720, null, DeviceCategory.DESKTOP)))
        view.device = DeviceSelection(DeviceSelection.presets.first { it.name == "iPhone 14" }, 390, 844)
        actions.onHover(view, InspectorMessage.AnnotationHover("7", null))
        assertThat(view.itemPopover).isNull()
    }

    @Test
    fun `selecting emphasises the pin and deselecting clears it`() {
        view.selectedItemId = 7
        actions.emphasiseSelection(view)
        assertThat(host.scripts.last()).contains("selectAnnotation").contains("\"7\"")
        view.selectedItemId = null
        actions.emphasiseSelection(view)
        assertThat(host.scripts.last()).contains("\"\"")
    }

    @Test
    fun `delete removes the item and closes the popover and selection`() {
        val removed = mutableListOf<WebItem>()
        view.selectedItemId = 7
        view.itemPopover = ItemPopoverState("7", null)
        actions.delete(view, item, removed::add)
        assertThat(removed).containsExactly(item)
        assertThat(view.itemPopover).isNull()
        assertThat(view.selectedItemId).isNull()
    }
}
