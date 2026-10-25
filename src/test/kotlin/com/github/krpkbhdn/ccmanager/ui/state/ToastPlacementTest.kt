package com.github.krpkbhdn.ccmanager.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToastPlacementTest {
    @Test
    fun `an active window keeps the toast in the app`() {
        assertThat(toastPlaceFor(true)).isEqualTo(ToastPlace.IN_APP)
    }

    @Test
    fun `an inactive window sends the toast to the desktop`() {
        assertThat(toastPlaceFor(false)).isEqualTo(ToastPlace.DESKTOP_POPUP)
    }

    @Test
    fun `popups stack upward from the bottom-right corner`() {
        val area = ScreenArea(0, 0, 1920, 1040)
        assertThat(popupOrigin(area, 0, 400, 150, 10)).isEqualTo(1510 to 880)
        assertThat(popupOrigin(area, 1, 400, 150, 10)).isEqualTo(1510 to 730)
    }

    @Test
    fun `popups respect the screen offset`() {
        assertThat(popupOrigin(ScreenArea(1920, 40, 1280, 960), 0, 400, 150, 0)).isEqualTo(2800 to 850)
    }
}
