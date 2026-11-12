package com.github.krpkbhdn.ccmanager.ui.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InspectorBridgeTest {
    private val nasty = "a\"b\\c</script>\n  '"

    @Test
    fun hostileStringsStayInsideTheLiteral() {
        val scripts = listOf(
            InspectorBridge.focusAnnotation(nasty),
            InspectorBridge.previewStyle(nasty, mapOf("color" to nasty)),
            InspectorBridge.setAnnotations(listOf(AnnotationPin(nasty, 1, AnnotationAnchor(nasty, nasty, nasty, nasty, null), nasty))),
        )
        scripts.forEach {
            assertThat(it).doesNotContain("</", "\n", " ", " ").contains("\\\"b\\\\c")
        }
    }

    @Test
    fun callsAreGuardedAndShaped() {
        assertThat(InspectorBridge.clearPreview()).isEqualTo("try{window.__ccm&&window.__ccm.clearPreview()}catch(e){}")
        assertThat(InspectorBridge.setOptions(InspectorToggles(pick = true)))
            .contains("""{"pick":true,"showAll":false,"showBox":true,"showLabels":true,"similar":false,"spacing":[]}""")
        assertThat(InspectorBridge.previewStyle("a", mapOf("color" to "red"))).contains("""{"selector":"a","props":{"color":"red"}}""")
    }

    @Test
    fun bootstrapDefinesHostShimBeforeInspector() {
        val script = InspectorBridge.bootstrapScript(listOf("/*one*/", "/*two*/"))
        assertThat(script).contains("cefQuery").endsWith("})();")
        assertThat(script.indexOf("__ccmHost")).isLessThan(script.indexOf("/*one*/"))
        assertThat(script.indexOf("/*one*/")).isLessThan(script.indexOf("/*two*/"))
        assertThat(script.indexOf("__ccm.version")).isLessThan(script.indexOf("/*one*/"))
        assertThat(script.split("try {").size - 1).isEqualTo(2)
    }

    @Test
    fun newCallsCarryTheirArguments() {
        assertThat(InspectorBridge.stackAt(1.5, 2.0)).isEqualTo("try{window.__ccm&&window.__ccm.stackAt(1.5,2.0)}catch(e){}")
        assertThat(InspectorBridge.highlight("a > b")).contains("""highlight("a > b")""")
        assertThat(InspectorBridge.describeSelector("a", "menu")).contains("""describeSelector("a","menu")""")
        assertThat(InspectorBridge.selectAnnotation("7")).contains("""selectAnnotation("7")""")
        assertThat(InspectorBridge.setOptions(InspectorToggles(similar = true, spacing = setOf(SpacingLayer.GAP, SpacingLayer.VALUES))))
            .contains("\"similar\":true,\"spacing\":[\"GAP\",\"VALUES\"]")
        assertThat(InspectorBridge.describeSelector(nasty, nasty)).doesNotContain("</", "\n")
    }
}
