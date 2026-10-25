package com.github.krpkbhdn.ccmanager.ui.state

enum class ToastPlace { IN_APP, DESKTOP_POPUP }

/** A session notification is shown once: inside the window the user is looking at, otherwise as a desktop popup. */
fun toastPlaceFor(windowActive: Boolean): ToastPlace = if (windowActive) ToastPlace.IN_APP else ToastPlace.DESKTOP_POPUP

/** Top-left corner of popup number [index] (0 = lowest) stacked upward in the bottom-right corner of [area]. */
fun popupOrigin(area: ScreenArea, index: Int, popupWidth: Int, slotHeight: Int, margin: Int): Pair<Int, Int> =
    (area.x + area.width - popupWidth - margin) to (area.y + area.height - margin - (index + 1) * slotHeight)
