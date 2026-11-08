package com.github.krpkbhdn.ccmanager.ui.presets.apply

import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.AGENT
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.FILE
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.INSTRUCTION
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.MARKETPLACE
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.MCP_SERVER
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.PLUGIN
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.SETTINGS
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.SKILL
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.preset.apply.ChangeAction
import com.github.krpkbhdn.ccmanager.preset.apply.ComponentOutcome
import com.github.krpkbhdn.ccmanager.preset.apply.OutcomeStatus
import com.github.krpkbhdn.ccmanager.preset.apply.PlannedChange
import com.github.krpkbhdn.ccmanager.ui.presets.SEPARATOR

enum class ApplyStep(val label: String) { CHOOSE("Choose"), REVIEW("Review"), RESULT("Result") }

/** One kind's block of the review list; the plugin block also holds the marketplaces, listed first. */
class ChangeGroup(val kind: PresetComponentKind, val title: String, val changes: List<PlannedChange>)

private val groupOrder = listOf(
    INSTRUCTION to "Instructions",
    SKILL to "Skills",
    PLUGIN to "Plugins",
    MCP_SERVER to "MCP servers",
    AGENT to "Agents",
    FILE to "Files",
    SETTINGS to "Settings",
)

/** Changes grouped by kind in the order of the review list; kinds without a change are left out. */
fun groupChanges(plan: List<PlannedChange>): List<ChangeGroup> = groupOrder.mapNotNull { (kind, title) ->
    val members = plan.filter { (if (it.kind() == MARKETPLACE) PLUGIN else it.kind()) == kind }.sortedBy { if (it.kind() == MARKETPLACE) 0 else 1 }
    members.takeIf { it.isNotEmpty() }?.let { ChangeGroup(kind, title, it) }
}

private val writing = setOf(ChangeAction.CREATE, ChangeAction.UPDATE, ChangeAction.INSTALL)

/** Only changes that write something can be checked; the rest is information. */
fun isSelectable(change: PlannedChange): Boolean = change.action() in writing

fun defaultSelection(plan: List<PlannedChange>): Set<Long> = plan.filter(::isSelectable).map { it.componentId() }.toSet()

fun actionLabel(action: ChangeAction): String = when (action) {
    ChangeAction.CREATE -> "Create"
    ChangeAction.UPDATE -> "Update"
    ChangeAction.INSTALL -> "Install"
    ChangeAction.PRESENT -> "Already there"
    ChangeAction.CONFLICT -> "Conflict"
    ChangeAction.NEEDS_CONFIRMATION -> "Needs confirmation"
}

class PlanSummary(val changes: Int, val present: Int, val conflicts: Int, val confirmations: Int) {
    /** "5 changes · 2 already there · 1 conflict"; zero parts are left out. */
    fun line(): String = listOfNotNull(
        count(changes, "change", "changes") ?: "No changes",
        present.takeIf { it > 0 }?.let { "$it already there" },
        count(conflicts, "conflict", "conflicts"),
        confirmations.takeIf { it > 0 }?.let { "$it to confirm later" },
    ).joinToString(SEPARATOR)

    val onlyPresent: Boolean get() = changes == 0 && conflicts == 0 && confirmations == 0 && present > 0
}

private fun count(n: Int, one: String, many: String): String? = n.takeIf { it > 0 }?.let { "$it ${if (it == 1) one else many}" }

fun planSummary(plan: List<PlannedChange>) = PlanSummary(
    changes = plan.count(::isSelectable),
    present = plan.count { it.action() == ChangeAction.PRESENT },
    conflicts = plan.count { it.action() == ChangeAction.CONFLICT },
    confirmations = plan.count { it.action() == ChangeAction.NEEDS_CONFIRMATION },
)

/** The primary button of the review step. */
fun applyLabel(selected: Int): String = when (selected) {
    0 -> "Apply"
    1 -> "Apply 1 change"
    else -> "Apply $selected changes"
}

/** Applying needs a checked change, except when the project already has everything: then it only records the preset as applied. */
fun canApply(plan: List<PlannedChange>?, selection: Set<Long>): Boolean = plan != null && (selection.isNotEmpty() || planSummary(plan).onlyPresent)

enum class PresetStatus(val label: String) { APPLIED("Applied"), OUTDATED("Outdated") }

/** [appliedRevision] is the revision the project applied, null when it never did. */
fun presetStatus(preset: ProjectPreset, appliedRevision: Int?): PresetStatus? = when {
    appliedRevision == null -> null
    preset.revision() > appliedRevision -> PresetStatus.OUTDATED
    else -> PresetStatus.APPLIED
}

fun statusLabel(status: OutcomeStatus): String = when (status) {
    OutcomeStatus.APPLIED -> "Applied"
    OutcomeStatus.SKIPPED -> "Skipped"
    OutcomeStatus.FAILED -> "Failed"
    OutcomeStatus.NEEDS_CONFIRMATION -> "Needs confirmation"
    OutcomeStatus.SIGN_IN_NEEDED -> "Sign-in needed"
}

/** What the user must do next for an outcome, or the backend's own message. */
fun outcomeNote(outcome: ComponentOutcome): String = when (outcome.status()) {
    OutcomeStatus.SIGN_IN_NEEDED -> "Sign in on the MCP servers page"
    OutcomeStatus.NEEDS_CONFIRMATION -> "Confirm on the Plugins page"
    else -> outcome.message().orEmpty()
}

/** "3 applied · 1 skipped"; empty parts are left out. */
fun resultLine(outcomes: List<ComponentOutcome>): String {
    fun of(vararg statuses: OutcomeStatus) = outcomes.count { it.status() in statuses }
    val parts = listOfNotNull(
        of(OutcomeStatus.APPLIED, OutcomeStatus.SIGN_IN_NEEDED).takeIf { it > 0 }?.let { "$it applied" },
        of(OutcomeStatus.FAILED).takeIf { it > 0 }?.let { "$it failed" },
        of(OutcomeStatus.NEEDS_CONFIRMATION).takeIf { it > 0 }?.let { "$it to confirm" },
        of(OutcomeStatus.SKIPPED).takeIf { it > 0 }?.let { "$it skipped" },
    )
    return if (parts.isEmpty()) "Nothing was applied" else parts.joinToString(SEPARATOR)
}
