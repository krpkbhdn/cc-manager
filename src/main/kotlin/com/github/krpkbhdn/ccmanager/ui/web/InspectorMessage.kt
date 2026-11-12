package com.github.krpkbhdn.ccmanager.ui.web

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

data class Rect(val x: Double, val y: Double, val w: Double, val h: Double)

data class BoxEdges(val t: Double, val r: Double, val b: Double, val l: Double)

data class BoxModel(val margin: BoxEdges, val border: BoxEdges, val padding: BoxEdges, val contentW: Double, val contentH: Double)

data class SourceHint(val component: String?, val file: String?, val line: Int?)

data class ContrastInfo(val ratio: Double, val fg: String, val bg: String, val large: Boolean, val aa: Boolean, val aaa: Boolean)

/** One layer of the stack under a point: enough to list it and to find it again by selector. */
data class LiteTarget(val selector: String, val tag: String, val id: String, val stableClasses: List<String>, val rect: Rect?)

data class ElementTarget(
    val selector: String,
    val xpath: String,
    val tag: String,
    val id: String,
    val classes: List<String>,
    val text: String,
    val outerHtml: String,
    val rect: Rect?,
    val viewportRect: Rect?,
    val computed: Map<String, String>,
    val box: BoxModel?,
    val sourceHint: SourceHint?,
    val frame: String?,
    val pageUrl: String?,
    val contrast: ContrastInfo? = null,
    val similarCount: Int = 0,
    val stableClasses: List<String> = emptyList(),
)

/** A message sent by the injected inspector through `window.__ccmHost`. */
sealed interface InspectorMessage {
    data class Ready(val url: String, val title: String) : InspectorMessage
    data class Navigated(val url: String, val title: String) : InspectorMessage
    data class Hover(val target: ElementTarget) : InspectorMessage
    data class Pick(val target: ElementTarget) : InspectorMessage
    data object PickCancel : InspectorMessage
    data class AnnotationClick(val id: String) : InspectorMessage
    data class Stack(val x: Double, val y: Double, val items: List<LiteTarget>) : InspectorMessage
    data class Inspect(val reason: String, val target: ElementTarget) : InspectorMessage
    data class AnnotationHover(val id: String, val rect: Rect?) : InspectorMessage
    data object AnnotationLeave : InspectorMessage
    data class Editing(val on: Boolean) : InspectorMessage
    data class ModuleError(val module: String, val message: String) : InspectorMessage

    companion object {
        const val MAX_MESSAGE_CHARS = 64 * 1024

        private val mapper = JsonMapper.builder().build()

        /** Untrusted page input: returns null for anything malformed, oversized or of the wrong type, never throws. */
        fun parse(json: String?): InspectorMessage? {
            if (json == null || json.length > MAX_MESSAGE_CHARS) return null
            return try {
                val root = mapper.readTree(json)
                if (root == null || !root.isObject) null else InspectorMessageReader.read(root)
            } catch (_: Exception) {
                null
            } catch (_: StackOverflowError) {
                null
            }
        }
    }
}

private object InspectorMessageReader {
    private const val MAX_URL = 2000
    private const val MAX_TITLE = 500
    private const val MAX_SELECTOR = 1000
    private const val MAX_TEXT = 200
    private const val MAX_HTML = 2000
    private const val MAX_VALUE = 200
    private const val MAX_COMPUTED = 60
    private const val MAX_CLASSES = 30
    private const val MAX_STACK = 20
    private const val MAX_SIMILAR = 100_000
    private const val MAX_COORDINATE = 1e6

    fun read(node: JsonNode): InspectorMessage? = when (text(node, "type", 50)) {
        "ready" -> InspectorMessage.Ready(text(node, "url", MAX_URL).orEmpty(), text(node, "title", MAX_TITLE).orEmpty())
        "navigated" -> InspectorMessage.Navigated(text(node, "url", MAX_URL).orEmpty(), text(node, "title", MAX_TITLE).orEmpty())
        "hover" -> target(node)?.let { InspectorMessage.Hover(it) }
        "pick" -> target(node)?.let { InspectorMessage.Pick(it) }
        "pickCancel" -> InspectorMessage.PickCancel
        "annotationClick" -> idOf(node)?.let { InspectorMessage.AnnotationClick(it) }
        "stack" -> stack(node)
        "inspect" -> target(node)?.let { InspectorMessage.Inspect(text(node, "reason", MAX_VALUE).orEmpty(), it) }
        "annotationHover" -> idOf(node)?.let { InspectorMessage.AnnotationHover(it, rect(node, "rect")) }
        "annotationLeave" -> InspectorMessage.AnnotationLeave
        "editing" -> InspectorMessage.Editing(flag(node, "on"))
        "moduleError" -> InspectorMessage.ModuleError(text(node, "module", MAX_VALUE).orEmpty(), text(node, "message", MAX_VALUE).orEmpty())
        else -> null
    }

    private fun stack(node: JsonNode): InspectorMessage.Stack {
        val list = node.get("items")
        require(list != null && list.isArray)
        return InspectorMessage.Stack(number(node, "x"), number(node, "y"), list.take(MAX_STACK).map(::liteTarget))
    }

    private fun liteTarget(item: JsonNode): LiteTarget {
        require(item.isObject)
        return LiteTarget(
            selector = token(item, "selector", MAX_SELECTOR) ?: error("missing selector"),
            tag = token(item, "tag", MAX_VALUE).orEmpty(),
            id = token(item, "id", MAX_VALUE).orEmpty(),
            stableClasses = strings(item, "stableClasses"),
            rect = rect(item, "rect"),
        )
    }

    private fun contrast(t: JsonNode): ContrastInfo? {
        val c = obj(t, "contrast") ?: return null
        return ContrastInfo(number(c, "ratio"), text(c, "fg", MAX_VALUE).orEmpty(), text(c, "bg", MAX_VALUE).orEmpty(), flag(c, "large"), flag(c, "aa"), flag(c, "aaa"))
    }

    private fun flag(node: JsonNode, name: String): Boolean {
        val v = node.get(name)
        require(v != null && v.isBoolean)
        return v.asBoolean()
    }

    private fun similarCount(t: JsonNode): Int {
        val v = t.get("similarCount")
        if (v == null || v.isNull) return 0
        require(v.isIntegralNumber)
        return v.asInt().coerceIn(0, MAX_SIMILAR)
    }

    private fun idOf(node: JsonNode): String? {
        val id = node.get("id") ?: return null
        return when {
            id.isString -> id.asString().take(MAX_VALUE).also(::requireNoControl)
            id.isIntegralNumber -> id.asLong().toString()
            else -> null
        }
    }

    private fun target(message: JsonNode): ElementTarget? {
        val t = message.get("target")?.takeIf { it.isObject } ?: return null
        val selector = token(t, "selector", MAX_SELECTOR) ?: return null
        return ElementTarget(
            selector = selector,
            xpath = token(t, "xpath", MAX_SELECTOR).orEmpty(),
            tag = token(t, "tag", MAX_VALUE).orEmpty(),
            id = token(t, "id", MAX_VALUE).orEmpty(),
            classes = strings(t, "classes"),
            text = flat(t, "text", MAX_TEXT).orEmpty(),
            outerHtml = flat(t, "outerHtml", MAX_HTML).orEmpty(),
            rect = rect(t, "rect"),
            viewportRect = rect(t, "viewportRect"),
            computed = computed(t),
            box = box(t),
            sourceHint = sourceHint(t),
            frame = flat(t, "frame", MAX_VALUE),
            pageUrl = flat(t, "pageUrl", MAX_URL),
            contrast = contrast(t),
            similarCount = similarCount(t),
            stableClasses = strings(t, "stableClasses"),
        )
    }

    /** Missing or null gives null; a present value of another type rejects the whole message. */
    private fun text(node: JsonNode, name: String, max: Int): String? {
        val v = node.get(name)
        if (v == null || v.isNull) return null
        require(v.isString)
        return v.asString().take(max)
    }

    /** Selectors, ids and class names end up on the clipboard and in scripts, so a control or format character (a newline, a bidi mark, a zero-width space) rejects the message. */
    private fun token(node: JsonNode, name: String, max: Int): String? = text(node, name, max)?.also(::requireNoControl)

    /** Free text (visible text, markup, urls) keeps the message but turns every control or format character into a space, so a page cannot smuggle line breaks into a prompt. */
    private fun flat(node: JsonNode, name: String, max: Int): String? = text(node, name, max)?.map { if (isControl(it)) ' ' else it }?.joinToString("")

    private fun isControl(c: Char) = c.isISOControl() || c.category == CharCategory.FORMAT || c == '\u2028' || c == '\u2029'

    private fun requireNoControl(value: String) = require(value.none(::isControl))

    /** Finite and clamped, so Infinity or a huge value never reaches layout. */
    private fun number(node: JsonNode, name: String): Double {
        val v = node.get(name)
        require(v != null && v.isNumber)
        val d = v.asDouble()
        require(d.isFinite())
        return d.coerceIn(-MAX_COORDINATE, MAX_COORDINATE)
    }

    private fun obj(node: JsonNode, name: String): JsonNode? {
        val v = node.get(name)
        if (v == null || v.isNull) return null
        require(v.isObject)
        return v
    }

    private fun strings(t: JsonNode, name: String): List<String> {
        val v = t.get(name)
        if (v == null || v.isNull) return emptyList()
        require(v.isArray)
        return v.take(MAX_CLASSES).map { require(it.isString); it.asString().take(MAX_VALUE).also(::requireNoControl) }
    }

    private fun rect(t: JsonNode, name: String): Rect? =
        obj(t, name)?.let { Rect(number(it, "x"), number(it, "y"), number(it, "w"), number(it, "h")) }

    private fun computed(t: JsonNode): Map<String, String> {
        val c = obj(t, "computed") ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for ((key, value) in c.properties()) {
            if (result.size >= MAX_COMPUTED) break
            require(value.isString || value.isNumber)
            result[key.take(MAX_VALUE)] = value.asString().take(MAX_VALUE)
        }
        return result
    }

    private fun edges(box: JsonNode, name: String): BoxEdges {
        val e = obj(box, name) ?: error("missing $name")
        return BoxEdges(number(e, "t"), number(e, "r"), number(e, "b"), number(e, "l"))
    }

    private fun box(t: JsonNode): BoxModel? {
        val b = obj(t, "box") ?: return null
        val content = obj(b, "content") ?: error("missing content")
        return BoxModel(edges(b, "margin"), edges(b, "border"), edges(b, "padding"), number(content, "w"), number(content, "h"))
    }

    private fun sourceHint(t: JsonNode): SourceHint? {
        val s = obj(t, "sourceHint") ?: return null
        val line = s.get("line")?.takeIf { !it.isNull }
        require(line == null || line.isIntegralNumber)
        return SourceHint(text(s, "component", MAX_VALUE), text(s, "file", MAX_URL), line?.asInt())
    }
}
