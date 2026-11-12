package com.github.krpkbhdn.ccmanager.ui.web

import tools.jackson.databind.json.JsonMapper

data class AnnotationAnchor(
    val selector: String,
    val xpath: String,
    val text: String,
    val tag: String,
    val rect: Rect?,
)

data class AnnotationPin(val id: String, val n: Int, val anchor: AnnotationAnchor, val status: String)

/** Builds the JS source run in the page; every dynamic value goes through JSON encoding so it cannot break out of its literal. */
object InspectorBridge {
    private val mapper = JsonMapper.builder().build()

    /** The ordered module sources run in one shared closure; a module that throws does not stop the others. */
    fun bootstrapScript(sources: List<String>): String =
        "window.__ccmHost = window.__ccmHost || (window.cefQuery ? function (s) { " +
            "window.cefQuery({request: s, onSuccess: function () {}, onFailure: function () {}}); } : function () {});\n" +
            "(function () {\n'use strict';\nvar W = window;\nif (W.__ccm && W.__ccm.version) return;\nvar C = {};\n" +
            sources.joinToString("") { "try {\n$it\n} catch (e) {}\n" } +
            "})();"

    fun setOptions(toggles: InspectorToggles): String = call(
        "setOptions",
        mapOf(
            "pick" to toggles.pick,
            "showAll" to toggles.showAll,
            "showBox" to toggles.showBox,
            "showLabels" to toggles.showLabels,
            "similar" to toggles.similar,
            "spacing" to toggles.spacing.map { it.name },
            "contrast" to toggles.contrast,
        ),
    )

    private val pointerKinds = setOf("move", "click", "leave")

    /** Drives hover, pins and picking from the host while real input is not forwarded to the page; [kind] is move, click or leave. */
    fun pointer(kind: String, x: Double, y: Double): String {
        require(kind in pointerKinds) { "unknown pointer kind" }
        return call("pointer", kind, x, y)
    }

    fun scrollBy(dx: Double, dy: Double): String = call("scrollBy", dx, dy)

    fun stackAt(x: Double, y: Double): String = call("stackAt", x, y)

    fun highlight(selector: String): String = call("highlight", selector)

    fun describeSelector(selector: String, reason: String): String = call("describeSelector", selector, reason)

    /** Needs a `showSimilar` api in the page script: outlines every element like the one at [selector]. */
    fun showSimilar(selector: String): String = call("showSimilar", selector)

    fun selectAnnotation(id: String): String = call("selectAnnotation", id)

    fun setAnnotations(pins: List<AnnotationPin>): String = call("setAnnotations", pins.map(::pinJson))

    fun focusAnnotation(id: String): String = call("focusAnnotation", id)

    fun previewStyle(selector: String, props: Map<String, String>): String =
        call("previewStyle", mapOf("selector" to selector, "props" to props))

    fun clearPreview(): String = call("clearPreview")

    private fun pinJson(pin: AnnotationPin): Map<String, Any?> = mapOf(
        "id" to pin.id,
        "n" to pin.n,
        "anchor" to mapOf(
            "selector" to pin.anchor.selector,
            "xpath" to pin.anchor.xpath,
            "text" to pin.anchor.text,
            "tag" to pin.anchor.tag,
            "rect" to pin.anchor.rect?.let { mapOf("x" to it.x, "y" to it.y, "w" to it.w, "h" to it.h) },
        ),
        "status" to pin.status,
    )

    private fun call(function: String, vararg args: Any?): String =
        "try{window.__ccm&&window.__ccm.$function(${args.joinToString(",") { literal(it) }})}catch(e){}"

    private fun literal(value: Any?): String = mapper.writeValueAsString(value)
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
        .replace("</", "<\\/")
}
