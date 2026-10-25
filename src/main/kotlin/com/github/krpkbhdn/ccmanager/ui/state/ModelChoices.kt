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

    /** [blockedReason] is why a command cannot be typed into the session right now, null when it can. */
    data class Running(val ccmSessionId: String, val blockedReason: String?) : ModelTarget
}

fun modelTarget(session: SessionSnapshot?, applying: Boolean, attached: Boolean = false): ModelTarget {
    if (session == null) return ModelTarget.NextLaunch
    val reason = when {
        applying -> "Applying the change…"
        session.claudeSessionId == null && !attached ->STARTING_MESSAGE
        session.status == AgentStatus.WORKING -> "Claude is working. Change the model when the session is free."
        session.status == AgentStatus.NEEDS_ACTION -> "Claude is waiting for you. Answer first, then change the model."
        else -> null
    }
    return ModelTarget.Running(session.ccmSessionId, reason)
}

/** The effort levels a running session reported that the picker knows; anything else selects nothing. */
fun knownEffort(effort: String?): String? = effort?.takeIf { it in ModelSelection.EFFORT_LEVELS }

/** `/model <alias or id>`; no model means claude's own default. @throws IllegalArgumentException for a name claude would not take */
fun modelCommand(model: String?): String = "/model " + (ModelSelection(model, null).model() ?: DEFAULT_MODEL_ARGUMENT)

/** `/effort <level>`; no level means claude's automatic choice. @throws IllegalArgumentException for an unknown level */
fun effortCommand(effort: String?): String = "/effort " + (ModelSelection(null, effort).effort() ?: DEFAULT_EFFORT_ARGUMENT)

/** True once a statusLine model name such as "Sonnet 5.5" belongs to the requested alias or id such as "sonnet" or "claude-sonnet-5-5". */
fun modelReported(reportedName: String?, requested: String?): Boolean {
    val reported = reportedName?.let(::letters)?.takeIf { it.isNotEmpty() } ?: return false
    val wanted = requested?.let(::letters)?.takeIf { it.isNotEmpty() } ?: return false
    return reported.contains(wanted) || wanted.contains(reported)
}

private fun letters(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

private const val DEFAULT_MODEL_ARGUMENT = "default"
private const val DEFAULT_EFFORT_ARGUMENT = "auto"
