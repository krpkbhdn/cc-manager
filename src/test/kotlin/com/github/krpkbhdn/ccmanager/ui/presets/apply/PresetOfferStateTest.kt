package com.github.krpkbhdn.ccmanager.ui.presets.apply

import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.await
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PresetOfferStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val applications = mock(PresetApplicationService::class.java)
    private val project = Project(7, "demo", Path.of("demo"), Instant.EPOCH)
    private val group = Project(8, "services", Path.of("services"), Instant.EPOCH, com.github.krpkbhdn.ccmanager.project.ModelSelection.DEFAULT, true, null)
    private val presets = listOf(ProjectPreset(1, "Spring", "", null, false, false, 1, emptyList()), ProjectPreset(2, "General", "", "general", false, false, 1, emptyList()))
    private var blocked = false
    private val reviewed = mutableListOf<Pair<Long, Long>>()
    private val state = PresetOfferState(applications, { presets }, { id -> listOf(project, group).firstOrNull { it.id == id } }, scope, { blocked }, { p, preset -> reviewed += p to preset }, {})

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `a project without an application is offered with general preselected`() {
        state.offerFor(7)

        await { state.offer != null }
        assertThat(state.offer!!.presetId).isEqualTo(2)
    }

    @Test
    fun `a project that applied a preset is not offered`() {
        `when`(applications.applications(7)).thenReturn(mapOf(1L to 1))

        state.offerFor(7)

        verify(applications, timeout(2000)).applications(7)
        Thread.sleep(50)
        assertThat(state.offer).isNull()
    }

    @Test
    fun `a declined project is not offered`() {
        `when`(applications.offerDeclined(7)).thenReturn(true)

        state.offerFor(7)

        verify(applications, timeout(2000)).offerDeclined(7)
        Thread.sleep(50)
        assertThat(state.offer).isNull()
    }

    @Test
    fun `groups are never offered`() {
        state.offerFor(8)

        Thread.sleep(50)
        assertThat(state.offer).isNull()
    }

    @Test
    fun `an open dialog skips the offer without remembering it`() {
        blocked = true
        state.offerFor(7)
        Thread.sleep(50)
        assertThat(state.offer).isNull()

        blocked = false
        state.offerFor(7)

        await { state.offer != null }
    }

    @Test
    fun `later asks again next run only, not on the next visit of the same run`() {
        state.offerFor(7)
        await { state.offer != null }
        state.later()

        state.offerFor(7)

        Thread.sleep(50)
        assertThat(state.offer).isNull()
    }

    @Test
    fun `not for this project records the decline`() {
        state.offerFor(7)
        await { state.offer != null }

        state.decline()

        verify(applications, timeout(2000)).declineOffer(7)
        assertThat(state.offer).isNull()
    }

    @Test
    fun `review and apply hands the chosen preset on`() {
        state.offerFor(7)
        await { state.offer != null }
        state.choose(1)

        state.reviewAndApply()

        assertThat(reviewed).containsExactly(7L to 1L)
        assertThat(state.offer).isNull()
    }
}
