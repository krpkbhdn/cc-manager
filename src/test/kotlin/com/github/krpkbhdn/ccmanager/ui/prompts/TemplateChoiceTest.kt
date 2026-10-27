package com.github.krpkbhdn.ccmanager.ui.prompts

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TemplateChoiceTest {
    private fun template(id: Long, projectId: Long? = null, teamId: Long? = null) = PromptTemplate(id, "t$id", "", "text", projectId, teamId)
    private val projectTeam = Team(1, "Project team", emptyList())
    private val templateTeam = Team(2, "Template team", emptyList())

    @Test
    fun templatesOfTheProjectComeFirstKeepingOrder() {
        val ordered = pickerOrder(listOf(template(1), template(2, projectId = 7), template(3), template(4, projectId = 7)), 7)

        assertThat(ordered.map { it.id }).containsExactly(2L, 4L, 1L, 3L)
    }

    @Test
    fun selectionWrapsAroundBothWays() {
        assertThat(movedSelection(2, 1, 3)).isZero()
        assertThat(movedSelection(0, -1, 3)).isEqualTo(2)
        assertThat(movedSelection(0, 1, 0)).isZero()
    }

    @Test
    fun templateTeamWinsOverTheProjectDefault() {
        assertThat(teamForTemplate(template(1, teamId = 2), listOf(projectTeam, templateTeam), projectTeam)).isEqualTo(templateTeam)
    }

    @Test
    fun projectDefaultIsUsedWithoutOrWithAMissingTemplateTeam() {
        assertThat(teamForTemplate(template(1), listOf(projectTeam), projectTeam)).isEqualTo(projectTeam)
        assertThat(teamForTemplate(template(1, teamId = 2), listOf(projectTeam), projectTeam)).isEqualTo(projectTeam)
        assertThat(teamForTemplate(template(1), emptyList(), null)).isNull()
    }
}
