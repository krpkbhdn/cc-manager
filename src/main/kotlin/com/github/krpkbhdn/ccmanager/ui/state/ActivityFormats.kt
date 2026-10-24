package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ActivityTone { NEUTRAL, ACCENT, SUCCESS, DANGER }

fun activityTone(type: HookEventType): ActivityTone = when (type) {
    HookEventType.PERMISSION_REQUEST, HookEventType.ELICITATION, HookEventType.STOP_FAILURE,
    HookEventType.POST_TOOL_USE_FAILURE, HookEventType.PERMISSION_DENIED -> ActivityTone.DANGER
    HookEventType.STOP, HookEventType.TASK_COMPLETED -> ActivityTone.SUCCESS
    HookEventType.USER_PROMPT_SUBMIT, HookEventType.SESSION_START, HookEventType.SUBAGENT_START -> ActivityTone.ACCENT
    else -> ActivityTone.NEUTRAL
}

/** "PRE_TOOL_USE" becomes "pre tool use". */
fun eventLabel(type: HookEventType): String = type.name.lowercase().replace('_', ' ')

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

fun formatClock(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = clockFormat.format(at.atZone(zone))

fun lineMarker(kind: GitDiff.LineKind): String = when (kind) {
    GitDiff.LineKind.ADDED -> "+"
    GitDiff.LineKind.REMOVED -> "-"
    else -> " "
}

fun lineNumberText(number: Int?): String = number?.toString() ?: ""

fun renameLabel(file: GitDiff.FileDiff): String =
    if (file.change == GitDiff.ChangeType.RENAMED && file.oldPath != null) "${file.oldPath} -> ${file.path}" else file.path
