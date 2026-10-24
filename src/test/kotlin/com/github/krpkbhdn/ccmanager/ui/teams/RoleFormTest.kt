package com.github.krpkbhdn.ccmanager.ui.teams

import com.github.krpkbhdn.ccmanager.team.RoleDraft
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoleFormTest {
    private fun draft(name: String, lead: Boolean = false, model: String? = null) = RoleDraft(name, "when $name", "You are $name", model, null, null, lead)

    @Test
    fun formRoundTripsADraft() {
        val source = RoleDraft("qa", "Checks.", "You test.", "haiku", "low", "Read, Bash", false)

        assertThat(RoleForm(source).draft()).isEqualTo(source)
    }

    @Test
    fun errorFollowsTheBackendRulesAndClearsWhenFixed() {
        val form = RoleForm(draft("dev"))
        assertThat(form.error).isNull()

        form.model = "-evil"
        assertThat(form.error).contains("model")
        form.model = "haiku"
        form.name.edit { replace(0, length, "Bad Name") }
        assertThat(form.error).contains("Role name")
    }

    @Test
    fun anEmptyToolAllowlistBlocksSaving() {
        val form = RoleForm(draft("dev"))

        form.tools = ToolSelection(emptyList())

        assertThat(form.error).contains("at least one tool")
    }

    @Test
    fun dirtyTracksEditsAndReturnsToCleanWhenUndone() {
        val form = RoleForm(draft("dev"))
        assertThat(form.dirty).isFalse()

        form.effort = "high"
        assertThat(form.dirty).isTrue()
        form.effort = null
        assertThat(form.dirty).isFalse()
    }

    @Test
    fun roleLabelsSayWhatAMissingModelMeans() {
        val team = Team(1, "Squad", listOf(TeamRole(1, draft("boss", lead = true), 0), TeamRole(2, draft("dev"), 1), TeamRole(3, draft("qa", model = "haiku"), 2)))

        assertThat(team.roles.map(::roleModelLabel)).containsExactly("Default model", "Inherits", "Haiku")
        assertThat(team.subagentRole("qa")?.name()).isEqualTo("qa")
        assertThat(team.subagentRole("boss")).isNull()
        assertThat(team.slotOf(team.roles[2])).isEqualTo(2)
    }
}
