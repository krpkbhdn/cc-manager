package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.web.WebItem

/** Reacts to the pins: the hover popover, the selection (emphasised in the page) and the popover's actions. */
internal class WebPinActions {
    /** Only an id of an item in the current list shows a popover, so a forged hover shows nothing. */
    fun onHover(view: WebView, message: InspectorMessage.AnnotationHover) {
        if (view.visibleItems.none { it.id().toString() == message.id }) return
        view.itemPopover = ItemPopoverState(message.id, message.rect)
    }

    fun onLeave(view: WebView) {
        view.itemPopover = null
    }

    fun emphasiseSelection(view: WebView) {
        view.host.runScript(InspectorBridge.selectAnnotation(view.selectedItemId?.toString().orEmpty()))
    }

    fun delete(view: WebView, item: WebItem, remove: (WebItem) -> Unit) {
        remove(item)
        view.itemPopover = null
        if (view.selectedItemId == item.id()) view.selectedItemId = null
    }
}
