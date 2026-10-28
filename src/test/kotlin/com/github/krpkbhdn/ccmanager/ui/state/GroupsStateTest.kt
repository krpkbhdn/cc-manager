package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.ui.PREVIEW_GROUP_ID
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.await
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class GroupsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backend = PreviewWorld().backend(PreviewTerminal)
    private val state = AppState(backend, scope, UiSettingsStore(null))

    @BeforeEach
    fun start() {
        state.start()
        await { state.projects.groups.isNotEmpty() }
    }

    @AfterEach
    fun stop() {
        state.dispose()
        scope.cancel()
    }

    @Test
    fun `the author session project is never a group even while the group is open`() {
        state.navigate(Route.Project(PREVIEW_GROUP_ID))

        assertThat(state.author.sessionProjectName).isNotEqualTo(state.projects.find(PREVIEW_GROUP_ID)!!.name)
        assertThat(state.author.sessionProjectName).isIn(state.projects.projects.map { it.name })
    }

    @Test
    fun `members are not listed as ungrouped and groups are not projects`() {
        val members = state.projects.membersOf(PREVIEW_GROUP_ID)

        assertThat(members).isNotEmpty()
        assertThat(state.projects.ungrouped).doesNotContainAnyElementsOf(members)
        assertThat(state.projects.projects).noneMatch { it.group }
        assertThat(state.projects.topLevelCount).isEqualTo(state.projects.groups.size + state.projects.ungrouped.size)
    }

    @Test
    fun `a rejected member list keeps the group name and the dialog open`() {
        val group = state.projects.find(PREVIEW_GROUP_ID)!!
        val errors = CopyOnWriteArrayList<String>()
        state.projects.groupDialog = GroupDialogRequest(group)

        state.projects.saveGroup(group, "renamed", emptyList()) { errors += it }

        await { errors.isNotEmpty() }
        verify(backend.projects, never()).rename(anyLong(), anyString())
        assertThat(state.projects.find(PREVIEW_GROUP_ID)!!.name).isEqualTo(group.name)
        assertThat(state.projects.groupDialog).isNotNull()
    }

    @Test
    fun `saving a group replaces members renames it and closes the dialog`() {
        val group = state.projects.find(PREVIEW_GROUP_ID)!!
        val kept = state.projects.membersOf(PREVIEW_GROUP_ID).first()
        state.projects.groupDialog = GroupDialogRequest(group)

        state.projects.saveGroup(group, " renamed ", listOf(kept.id)) { }

        await { state.projects.groupDialog == null }
        verify(backend.projects).rename(PREVIEW_GROUP_ID, "renamed")
        await { state.projects.membersOf(PREVIEW_GROUP_ID).map { it.id } == listOf(kept.id) }
    }

    @Test
    fun `removing a group asks for confirmation and says its projects stay registered`() {
        val group = state.projects.find(PREVIEW_GROUP_ID)!!

        state.projects.requestRemove(group)

        assertThat(state.confirm!!.title).isEqualTo("Remove group")
        assertThat(state.confirm!!.message).contains("projects in it stay registered")
    }
}
