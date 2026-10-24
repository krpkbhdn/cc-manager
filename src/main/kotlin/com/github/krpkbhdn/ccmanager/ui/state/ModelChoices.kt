package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot

private const val DEFAULT_MODEL_LABEL = "Default model"

/** A model claude accepts by alias; [value] is what `--model` receives. */
data class ModelPreset(val value: String, val label: String)

val modelPresets = listOf(
    ModelPreset("opus", "Opus"),
    ModelPreset("sonnet", "Sonnet"),
    ModelPreset("haiku", "Haiku"),
    ModelPreset("fable", "Fable"),
    ModelPreset("opus[1m]", "Opus, 1M context"),
    ModelPreset("sonnet[1m]", "Sonnet, 1M context"),
    ModelPreset("opusplan", "Opus plans, Sonnet executes"),
)

fun isCustomModel(selection: ModelSelection): Boolean = selection.model() != null && modelPresets.none { it.value == selection.model() }

fun modelLabel(model: String?): String = model?.let { id -> modelPresets.firstOrNull { it.value == id }?.label ?: id } ?: DEFAULT_MODEL_LABEL

/** "Opus · high", "Default model", "Default model · low": the picker button's text. */
fun selectionLabel(selection: ModelSelection): String =
    listOfNotNull(modelLabel(selection.model()), selection.effort()).joinToString(" · ")

/** What a running session reported: "Opus 4.7 · xhigh", or null while it reported neither. */
fun effectiveModelLabel(modelName: String?, effort: String?): String? =
    listOfNotNull(modelName, effort).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** What the model control in the tab strip acts on: a running session, or only the default of the next one. */
sealed interface ModelTarget {
    data object NextLaunch : ModelTarget

    /** [blockedReason] is why switching is not possible right now, null when it is. */
    data class Running(val ccmSessionId: String, val claudeSessionId: String?, val blockedReason: String?) : ModelTarget
}

fun modelTarget(session: SessionSnapshot?, switching: Boolean): ModelTarget {
    if (session == null) return ModelTarget.NextLaunch
    val reason = when {
        switching -> "Switching the model…"
        session.status == AgentStatus.WORKING -> "Claude is working. Switch when the session is free."
        session.status == AgentStatus.NEEDS_ACTION -> "Claude is waiting for you. Answer first, then switch."
        session.claudeSessionId == null -> "The session has no conversation to resume yet."
        else -> null
    }
    return ModelTarget.Running(session.ccmSessionId, session.claudeSessionId, reason)
}

/** The effort levels a running session reported that the picker knows; anything else selects nothing. */
fun knownEffort(effort: String?): String? = effort?.takeIf { it in ModelSelection.EFFORT_LEVELS }
