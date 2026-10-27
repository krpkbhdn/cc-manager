package com.github.krpkbhdn.ccmanager.ui.prompts

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team

/** Templates bound to [projectId] come before global ones; each group keeps its incoming order. */
fun pickerOrder(templates: List<PromptTemplate>, projectId: Long): List<PromptTemplate> =
    templates.filter { it.projectId == projectId } + templates.filter { it.projectId != projectId }

/** Index after moving [delta] steps, wrapping around; 0 for an empty list. */
fun movedSelection(current: Int, delta: Int, size: Int): Int = if (size == 0) 0 else Math.floorMod(current + delta, size)

/** The template's default team when that team still exists, otherwise [fallback] (the project's default team). */
fun teamForTemplate(template: PromptTemplate, teams: List<Team>, fallback: Team?): Team? =
    template.defaultTeamId?.let { id -> teams.firstOrNull { it.id == id } } ?: fallback
