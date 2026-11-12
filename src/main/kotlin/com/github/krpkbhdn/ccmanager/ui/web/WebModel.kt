package com.github.krpkbhdn.ccmanager.ui.web

enum class ConsoleLevel { DEBUG, LOG, INFO, WARN, ERROR }

data class ConsoleLine(
    val level: ConsoleLevel,
    val text: String,
    val source: String,
    val line: Int,
    val time: Long,
    val repeat: Int = 1,
)

/** Bounded console log: oldest lines drop first, identical consecutive lines collapse into a counter. */
class ConsoleBuffer(private val limit: Int = 500) {
    private val items = ArrayDeque<ConsoleLine>()

    fun add(line: ConsoleLine) {
        val last = items.lastOrNull()
        if (last != null && last.level == line.level && last.text == line.text && last.source == line.source && last.line == line.line) {
            items[items.lastIndex] = last.copy(repeat = last.repeat + 1, time = line.time)
            return
        }
        items.addLast(line)
        while (items.size > limit) items.removeFirst()
    }

    fun lines(levels: Set<ConsoleLevel> = ConsoleLevel.entries.toSet()): List<ConsoleLine> = items.filter { it.level in levels }

    fun clear() = items.clear()
}

data class StyleChange(val property: String, val from: String, val to: String)

data class CssSides(val top: String, val right: String, val bottom: String, val left: String)

object StyleDiff {
    private val shorthands = setOf("padding", "margin")

    /** Edited properties that differ from the computed ones; `padding`/`margin` shorthands expand to their sides. */
    fun changes(computed: Map<String, String>, edited: Map<String, String>): List<StyleChange> =
        expand(edited).mapNotNull { (property, to) ->
            val from = computed[property].orEmpty()
            if (from.trim() == to.trim()) null else StyleChange(property, from, to)
        }

    /** CSS 1 to 4 value syntax; null when empty or more than four values. */
    fun parseSides(value: String): CssSides? {
        val p = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (p.size) {
            1 -> CssSides(p[0], p[0], p[0], p[0])
            2 -> CssSides(p[0], p[1], p[0], p[1])
            3 -> CssSides(p[0], p[1], p[2], p[1])
            4 -> CssSides(p[0], p[1], p[2], p[3])
            else -> null
        }
    }

    fun formatSides(s: CssSides): String = when {
        s.top == s.right && s.right == s.bottom && s.bottom == s.left -> s.top
        s.top == s.bottom && s.right == s.left -> "${s.top} ${s.right}"
        s.right == s.left -> "${s.top} ${s.right} ${s.bottom}"
        else -> "${s.top} ${s.right} ${s.bottom} ${s.left}"
    }

    private fun expand(edited: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((property, value) in edited) {
            val sides = if (property in shorthands) parseSides(value) else null
            if (sides == null) {
                result[property] = value
            } else {
                result["$property-top"] = sides.top
                result["$property-right"] = sides.right
                result["$property-bottom"] = sides.bottom
                result["$property-left"] = sides.left
            }
        }
        return result
    }
}

enum class SpacingLayer { MARGIN, PADDING, GAP, VALUES, BORDERS, PHANTOMS, MISALIGN }

enum class OverlayGroup(val key: Char) { INSPECT('1'), SPACING('2'), AUDIT('3'), SIMILAR('4'), CONTRAST('5') }

private val spacingGroupLayers = setOf(SpacingLayer.MARGIN, SpacingLayer.PADDING, SpacingLayer.GAP, SpacingLayer.VALUES)

/**
 * The sub-options are remembered values; [overlaysOn] and [off] only mask them in [effective], so they survive any off/on.
 */
data class InspectorToggles(
    val pick: Boolean = false,
    val showAll: Boolean = false,
    val showBox: Boolean = true,
    val showLabels: Boolean = true,
    val similar: Boolean = false,
    val spacing: Set<SpacingLayer> = emptySet(),
    val overlaysOn: Boolean = true,
    val off: Set<OverlayGroup> = emptySet(),
    val contrast: Boolean = true,
) {
    fun isOn(group: OverlayGroup) = overlaysOn && group !in off

    /** What the page gets: sub-options of groups that are off (all of them when the master is off) cleared. */
    fun effective(): InspectorToggles = copy(
        showAll = showAll && isOn(OverlayGroup.INSPECT),
        showBox = showBox && isOn(OverlayGroup.INSPECT),
        showLabels = showLabels && isOn(OverlayGroup.INSPECT),
        similar = similar && isOn(OverlayGroup.SIMILAR),
        contrast = contrast && isOn(OverlayGroup.CONTRAST),
        spacing = spacing.filterTo(mutableSetOf()) { layer ->
            isOn(if (layer in spacingGroupLayers) OverlayGroup.SPACING else OverlayGroup.AUDIT)
        },
    )

    fun toggleMaster() = copy(overlaysOn = !overlaysOn)

    /** Flips a group; while the master is off it turns the master and this group on. */
    fun toggleGroup(group: OverlayGroup) = when {
        !overlaysOn -> copy(overlaysOn = true, off = off - group)
        group in off -> copy(off = off - group)
        else -> copy(off = off + group)
    }

    fun togglePick() = copy(pick = !pick)
    fun toggleShowAll() = copy(showAll = !showAll)
    fun toggleShowBox() = copy(showBox = !showBox)
    fun toggleShowLabels() = copy(showLabels = !showLabels)
}

/** Short label such as `button.save` or `div#app`, with the source location when the page exposes one. */
fun formatTargetTitle(target: ElementTarget): String {
    val base = when {
        target.tag.isNotEmpty() && target.id.isNotEmpty() -> "${target.tag}#${target.id}"
        target.tag.isNotEmpty() && target.classes.isNotEmpty() -> "${target.tag}.${target.classes.first()}"
        target.tag.isNotEmpty() -> target.tag
        else -> target.selector.substringAfterLast(" > ")
    }
    val hint = target.sourceHint ?: return base
    val location = listOfNotNull(hint.file?.let { f -> if (hint.line != null) "$f:${hint.line}" else f }, hint.component?.let { "<$it>" })
    return if (location.isEmpty()) base else "$base  ${location.joinToString(" ")}"
}
