package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.apply.ChangeAction
import com.github.krpkbhdn.ccmanager.preset.apply.ComponentOutcome
import com.github.krpkbhdn.ccmanager.preset.apply.OutcomeStatus
import com.github.krpkbhdn.ccmanager.preset.apply.PlannedChange
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyResult
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService
import java.nio.file.Path
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** What the preview's fake apply service answers; shots change it before opening the dialog. */
internal object PreviewApply {
    @Volatile
    var plan: List<PlannedChange> = samplePlan()

    @Volatile
    var planDelayMillis = 0L

    /** Non-empty by default so no project is offered a preset; the offer shot empties it. */
    @Volatile
    var applied: Map<Long, Int> = NOTHING_REAL

    @Volatile
    var outcomes: List<ComponentOutcome> = sampleOutcomes()

    fun reset() {
        plan = samplePlan()
        planDelayMillis = 0
        applied = NOTHING_REAL
        outcomes = sampleOutcomes()
    }
}

private val NOTHING_REAL = mapOf(-1L to 1)

private val sampleMarkdown = "## Rules\n\n- Keep changes small.\n- Run the tests before every commit.\n- Ask before adding a dependency.\n\n## Comments\n\n- Names explain the code; a comment says why.\n- No commented-out code.\n- No change history in comments.\n".repeat(3)

fun samplePlan(): List<PlannedChange> = listOf(
    PlannedChange(1, PresetComponentKind.INSTRUCTION, "Clean code", ChangeAction.UPDATE, "Replaces the managed block in CLAUDE.md", sampleMarkdown),
    PlannedChange(2, PresetComponentKind.INSTRUCTION, "Conventions", ChangeAction.CREATE, "Adds a managed block to CLAUDE.md", "## Conventions\n\n- Commits are one line.\n"),
    PlannedChange(3, PresetComponentKind.MARKETPLACE, "claude-plugins-official", ChangeAction.PRESENT, "Already added", null),
    PlannedChange(4, PresetComponentKind.PLUGIN, "context7@claude-plugins-official", ChangeAction.INSTALL, "Installs for this project", null),
    PlannedChange(5, PresetComponentKind.PLUGIN, "ponytail@claude-plugins-official", ChangeAction.INSTALL, "Installs for this project", null),
    PlannedChange(6, PresetComponentKind.SKILL, "dry-refactoring", ChangeAction.CREATE, "Copies 2 files to .claude/skills/dry-refactoring", null),
    PlannedChange(7, PresetComponentKind.SKILL, "code-review", ChangeAction.PRESENT, "Same as the preset", null),
    PlannedChange(8, PresetComponentKind.MCP_SERVER, "github", ChangeAction.CREATE, "Registers the server for this project", null),
    PlannedChange(9, PresetComponentKind.AGENT, "reviewer", ChangeAction.CONFLICT, "A different .claude/agents/reviewer.md exists; remove it or rename the agent to apply this one", "---\nname: reviewer\ndescription: Reviews a diff for missing tests and unclear names.\ntools: Read, Grep, Glob\n---\n\nYou review code changes.\n"),
    PlannedChange(10, PresetComponentKind.FILE, ".jscpd.json", ChangeAction.CREATE, "Creates the file and adds jscpd-report/ to .gitignore", "{\n  \"threshold\": 3\n}\n"),
    PlannedChange(11, PresetComponentKind.SETTINGS, "settings", ChangeAction.UPDATE, "Adds permissions.deny and env to .claude/settings.json", "{\n  \"permissions\": {\"deny\": [\"Read(./.env)\"]}\n}\n"),
)

fun sampleOutcomes(): List<ComponentOutcome> = listOf(
    ComponentOutcome(3, PresetComponentKind.MARKETPLACE, "claude-plugins-official", OutcomeStatus.SKIPPED, "Already added"),
    ComponentOutcome(4, PresetComponentKind.PLUGIN, "context7@claude-plugins-official", OutcomeStatus.APPLIED, "Installed"),
    ComponentOutcome(5, PresetComponentKind.PLUGIN, "ponytail@claude-plugins-official", OutcomeStatus.NEEDS_CONFIRMATION, "The plugin runs a command that needs your confirmation"),
    ComponentOutcome(8, PresetComponentKind.MCP_SERVER, "github", OutcomeStatus.SIGN_IN_NEEDED, "Registered, the server needs a sign-in"),
    ComponentOutcome(6, PresetComponentKind.SKILL, "dry-refactoring", OutcomeStatus.APPLIED, "Copied 2 files"),
    ComponentOutcome(9, PresetComponentKind.AGENT, "reviewer", OutcomeStatus.FAILED, "Could not write .claude/agents/reviewer.md"),
    ComponentOutcome(10, PresetComponentKind.FILE, ".jscpd.json", OutcomeStatus.APPLIED, "Created"),
    ComponentOutcome(1, PresetComponentKind.INSTRUCTION, "Clean code", OutcomeStatus.APPLIED, "Updated CLAUDE.md"),
    ComponentOutcome(11, PresetComponentKind.SETTINGS, "settings", OutcomeStatus.SKIPPED, "Not selected"),
)

fun previewPresetApply(): PresetApplyService {
    val service = mock(PresetApplyService::class.java)
    `when`(service.plan(anyLong(), any(Path::class.java))).thenAnswer {
        if (PreviewApply.planDelayMillis > 0) Thread.sleep(PreviewApply.planDelayMillis)
        PreviewApply.plan
    }
    `when`(service.apply(anyLong(), anyLong(), any(Path::class.java), any())).thenAnswer { PresetApplyResult(PreviewApply.outcomes, true) }
    return service
}

fun previewPresetApplications(): PresetApplicationService {
    val service = mock(PresetApplicationService::class.java)
    `when`(service.applications(anyLong())).thenAnswer { PreviewApply.applied }
    `when`(service.offerDeclined(anyLong())).thenReturn(false)
    return service
}
