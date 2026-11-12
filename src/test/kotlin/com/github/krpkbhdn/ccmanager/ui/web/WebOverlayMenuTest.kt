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

    private val all = InspectorToggles(
        showAll = true, similar = true, spacing = setOf(SpacingLayer.MARGIN, SpacingLayer.BORDERS),
    )

    @Test
    fun effectiveClearsOnlyGroupsThatAreOff() {
        val e = all.copy(off = setOf(OverlayGroup.INSPECT, OverlayGroup.AUDIT, OverlayGroup.CONTRAST)).effective()
        assertThat(e.showAll || e.showBox || e.showLabels || e.contrast).isFalse()
        assertThat(e.spacing).containsExactly(SpacingLayer.MARGIN)
        assertThat(e.similar).isTrue()
    }

    @Test
    fun effectiveClearsEverythingWhenMasterIsOffButKeepsRemembered() {
        val off = all.toggleMaster()
        val e = off.effective()
        assertThat(e.spacing).isEmpty()
        assertThat(e.similar || e.showAll || e.showBox || e.contrast).isFalse()
        assertThat(off.toggleMaster().effective()).isEqualTo(all.effective())
    }

    @Test
    fun groupOffOnKeepsSubOptions() {
        val back = all.toggleGroup(OverlayGroup.SPACING).toggleGroup(OverlayGroup.SPACING)
        assertThat(back).isEqualTo(all)
    }

    @Test
    fun groupToggleWhileMasterOffTurnsMasterAndGroupOn() {
        val t = all.copy(overlaysOn = false, off = setOf(OverlayGroup.SIMILAR)).toggleGroup(OverlayGroup.SIMILAR)
        assertThat(t.overlaysOn).isTrue()
        assertThat(t.isOn(OverlayGroup.SIMILAR)).isTrue()
    }

    @Test
    fun everyGroupHasMenuEntries() {
        OverlayGroup.entries.forEach { assertThat(groupEntries(it, all) {}).isNotEmpty() }
    }
}
