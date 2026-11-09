package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BadgeCenterTest {
    private val bounds = Rect(0f, 0f, 200f, 100f)

    @Test
    fun sitsUpLeftOfTheCornerByTheRadius() {
        assertThat(badgeCenter(Offset(50f, 40f), 10f, bounds)).isEqualTo(Offset(40f, 30f))
    }

    @Test
    fun staysInsideTheImageNearTheTopLeftEdge() {
        assertThat(badgeCenter(Offset(3f, 2f), 10f, bounds)).isEqualTo(Offset(10f, 10f))
    }

    @Test
    fun staysInsideTheImageNearTheBottomRightEdge() {
        assertThat(badgeCenter(Offset(250f, 150f), 10f, bounds)).isEqualTo(Offset(190f, 90f))
    }
}
