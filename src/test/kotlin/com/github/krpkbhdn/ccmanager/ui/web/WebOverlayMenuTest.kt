package com.github.krpkbhdn.ccmanager.ui.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebOverlayMenuTest {
    @Test
    fun masterItemTurnsOnMarginPaddingGapAndKeepsOthers() {
        val t = InspectorToggles(spacing = setOf(SpacingLayer.VALUES)).toggleAllSpacing()
        assertThat(t.spacing).containsExactlyInAnyOrder(SpacingLayer.MARGIN, SpacingLayer.PADDING, SpacingLayer.GAP, SpacingLayer.VALUES)
    }

    @Test
    fun masterItemTurnsAllThreeOffWhenAlreadyOn() {
        val t = InspectorToggles(spacing = setOf(SpacingLayer.MARGIN, SpacingLayer.PADDING, SpacingLayer.GAP, SpacingLayer.BORDERS)).toggleAllSpacing()
        assertThat(t.spacing).containsExactly(SpacingLayer.BORDERS)
    }

    @Test
    fun layerToggleAddsAndRemoves() {
        val on = InspectorToggles().toggleLayer(SpacingLayer.GAP)
        assertThat(on.spacing).containsExactly(SpacingLayer.GAP)
        assertThat(on.toggleLayer(SpacingLayer.GAP).spacing).isEmpty()
    }

    @Test
    fun countAndNoneCoverBasicAndSpacingOverlays() {
        val t = InspectorToggles(showAll = true, showBox = true, showLabels = false, spacing = setOf(SpacingLayer.MARGIN, SpacingLayer.MISALIGN))
        assertThat(overlayCount(t)).isEqualTo(4)
        assertThat(overlayCount(t.withoutOverlays())).isZero()
    }
}
