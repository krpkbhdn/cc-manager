package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.project.ModelSelection

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
