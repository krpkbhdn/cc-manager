package com.github.krpkbhdn.ccmanager.ui.screens

import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.SidebarSection
import com.github.krpkbhdn.ccmanager.ui.state.sidebarProjectOrder
import java.nio.file.Path
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SidebarNavTest {
    private fun project(id: Long, group: Boolean = false, groupId: Long? = null) =
        Project(id, "p$id", Path.of("p$id"), Instant.EPOCH, ModelSelection.DEFAULT, group, groupId)

    private val group = project(1, group = true)
    private val member = project(2, groupId = 1)
    private val loose = project(3)

    private fun order(folded: Set<Long>) =
        sidebarProjectOrder(listOf(group), { id -> listOf(member).filter { it.groupId == id } }, listOf(loose), folded).map { it.id }

    @Test
    fun `open group lists its members before ungrouped projects`() {
        assertThat(order(emptySet())).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `folded group hides its members`() {
        assertThat(order(setOf(1L))).containsExactly(1L, 3L)
    }

    @Test
    fun `sections hold the documented entries`() {
        assertThat(navSections.associate { it.section to it.entries.map { e -> e.route } }).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                SidebarSection.LIBRARY to listOf(Route.Teams, Route.Prompts),
                SidebarSection.EXTENSIONS to listOf(Route.Plugins, Route.Mcp, Route.Skills),
            ),
        )
    }

    @Test
    fun `second click inside the interval is a double click`() {
        assertThat(isDoubleClick(1000L, 1300L, 400L)).isTrue()
        assertThat(isDoubleClick(1000L, 1500L, 400L)).isFalse()
        assertThat(isDoubleClick(null, 1500L, 400L)).isFalse()
    }

    @Test
    fun `flyout stays open while pointer is over the icon or the flyout`() {
        assertThat(flyoutWanted(anchorHovered = true, flyoutHovered = false)).isTrue()
        assertThat(flyoutWanted(anchorHovered = false, flyoutHovered = true)).isTrue()
        assertThat(flyoutWanted(anchorHovered = false, flyoutHovered = false)).isFalse()
    }
}
