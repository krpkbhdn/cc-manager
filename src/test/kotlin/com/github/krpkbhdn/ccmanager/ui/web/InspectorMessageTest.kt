package com.github.krpkbhdn.ccmanager.ui.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InspectorMessageTest {
    private val target = """{"selector":"div > button.save","xpath":"/html/body/button","tag":"button","id":"","classes":["save"],
        "text":"Save","outerHtml":"<button>Save</button>","rect":{"x":1,"y":2,"w":3,"h":4},"viewportRect":{"x":1,"y":2,"w":3,"h":4},
        "computed":{"display":"block","z-index":3},"box":{"margin":{"t":1,"r":2,"b":3,"l":4},"border":{"t":0,"r":0,"b":0,"l":0},
        "padding":{"t":1,"r":1,"b":1,"l":1},"content":{"w":10,"h":20}},"sourceHint":{"component":"Foo","file":"src/Foo.tsx","line":42},
        "frame":null,"pageUrl":"http://x","unknown":1}"""

    @Test
    fun parsesEveryType() {
        assertThat(InspectorMessage.parse("""{"type":"ready","url":"u","title":"t"}""")).isEqualTo(InspectorMessage.Ready("u", "t"))
        assertThat(InspectorMessage.parse("""{"type":"navigated","url":"u","title":"t","extra":[]}""")).isEqualTo(InspectorMessage.Navigated("u", "t"))
        assertThat(InspectorMessage.parse("""{"type":"pickCancel"}""")).isEqualTo(InspectorMessage.PickCancel)
        assertThat(InspectorMessage.parse("""{"type":"annotationClick","id":"a1"}""")).isEqualTo(InspectorMessage.AnnotationClick("a1"))
        assertThat(InspectorMessage.parse("""{"type":"annotationClick","id":7}""")).isEqualTo(InspectorMessage.AnnotationClick("7"))
        val hover = InspectorMessage.parse("""{"type":"hover","target":$target}""") as InspectorMessage.Hover
        assertThat(hover.target.selector).isEqualTo("div > button.save")
        assertThat(hover.target.classes).containsExactly("save")
        assertThat(hover.target.computed).containsEntry("z-index", "3")
        assertThat(hover.target.box!!.margin.l).isEqualTo(4.0)
        assertThat(hover.target.sourceHint).isEqualTo(SourceHint("Foo", "src/Foo.tsx", 42))
        assertThat(InspectorMessage.parse("""{"type":"pick","target":$target}""")).isInstanceOf(InspectorMessage.Pick::class.java)
    }

    @Test
    fun malformedAndHostileInputGivesNull() {
        val deep = "[".repeat(100_000)
        val bad = listOf(
            null, "", "nope", "[]", "null", "42", "{", """{"type":"nope"}""", """{"type":5}""", """{"type":null}""", """{}""",
            """{"type":"hover"}""", """{"type":"hover","target":null}""", """{"type":"hover","target":"x"}""",
            """{"type":"hover","target":{"selector":5}}""", """{"type":"hover","target":{"tag":"a"}}""",
            """{"type":"pick","target":{"selector":"a","classes":"x"}}""", """{"type":"pick","target":{"selector":"a","rect":{"x":"1"}}}""",
            """{"type":"pick","target":{"selector":"a","computed":{"a":[1]}}}""", """{"type":"pick","target":{"selector":"a","box":{}}}""",
            """{"type":"annotationClick","id":[]}""", """{"type":"annotationClick"}""", """{"type":"ready","url":{}}""",
            deep, """{"type":"ready","url":"${"a".repeat(300_000)}"}""",
        )
        bad.forEach { assertThat(InspectorMessage.parse(it)).describedAs(it?.take(40)).isNull() }
    }

    @Test
    fun capsFieldLengthsAndCounts() {
        val computed = (1..100).joinToString(",") { "\"p$it\":\"${"v".repeat(300)}\"" }
        val classes = (1..100).joinToString(",") { "\"c$it\"" }
        val json = """{"type":"pick","target":{"selector":"${"s".repeat(5000)}","text":"${"t".repeat(900)}",
            "outerHtml":"${"h".repeat(9000)}","classes":[$classes],"computed":{$computed}}}"""
        val t = (InspectorMessage.parse(json) as InspectorMessage.Pick).target
        assertThat(t.selector).hasSize(1000)
        assertThat(t.text).hasSize(200)
        assertThat(t.outerHtml).hasSize(2000)
        assertThat(t.classes).hasSize(30)
        assertThat(t.computed).hasSize(60)
        assertThat(t.computed.values).allMatch { it.length == 200 }
    }

    @Test
    fun parsesStackInspectAndAnnotationHover() {
        val item = """{"selector":"div.a","tag":"div","id":"x","stableClasses":["a"],"rect":{"x":1,"y":2,"w":3,"h":4}}"""
        val stack = InspectorMessage.parse("""{"type":"stack","x":5,"y":6,"items":[$item]}""") as InspectorMessage.Stack
        assertThat(stack.x).isEqualTo(5.0)
        assertThat(stack.items).containsExactly(LiteTarget("div.a", "div", "x", listOf("a"), Rect(1.0, 2.0, 3.0, 4.0)))
        val inspect = InspectorMessage.parse(
            """{"type":"inspect","reason":"menu","target":${target.replace("\"unknown\":1", "\"similarCount\":4,\"stableClasses\":[\"save\"],\"contrast\":{\"ratio\":4.6,\"fg\":\"#000\",\"bg\":\"#fff\",\"large\":false,\"aa\":true,\"aaa\":false}")}}""",
        ) as InspectorMessage.Inspect
        assertThat(inspect.reason).isEqualTo("menu")
        assertThat(inspect.target.similarCount).isEqualTo(4)
        assertThat(inspect.target.stableClasses).containsExactly("save")
        assertThat(inspect.target.contrast).isEqualTo(ContrastInfo(4.6, "#000", "#fff", false, aa = true, aaa = false))
        assertThat(InspectorMessage.parse("""{"type":"annotationHover","id":"a1","rect":{"x":1,"y":2,"w":3,"h":4}}"""))
            .isEqualTo(InspectorMessage.AnnotationHover("a1", Rect(1.0, 2.0, 3.0, 4.0)))
        assertThat(InspectorMessage.parse("""{"type":"annotationLeave"}""")).isEqualTo(InspectorMessage.AnnotationLeave)
        assertThat((InspectorMessage.parse("""{"type":"hover","target":$target}""") as InspectorMessage.Hover).target.contrast).isNull()
    }

    @Test
    fun capsStackAndRejectsMalformedNewMessages() {
        val items = (1..30).joinToString(",") { """{"selector":"s$it"}""" }
        assertThat((InspectorMessage.parse("""{"type":"stack","x":0,"y":0,"items":[$items]}""") as InspectorMessage.Stack).items).hasSize(20)
        assertThat(InspectorMessage.parse("""{"type":"stack","x":"0","y":0,"items":[]}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"stack","x":0,"y":0,"items":[{"tag":"div"}]}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"stack","x":0,"y":0}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"annotationHover","rect":{}}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"inspect","reason":"r"}""")).isNull()
        val badContrast = """{"selector":"a","contrast":{"ratio":"x"}}"""
        assertThat(InspectorMessage.parse("""{"type":"inspect","reason":"r","target":$badContrast}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a","similarCount":"many"}}""")).isNull()
    }

    @Test
    fun controlCharactersInSelectorsAndNamesRejectTheMessage() {
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a\nrm -rf"}}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a","xpath":"/a\r"}}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a","classes":["x\ny"]}}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"stack","x":0,"y":0,"items":[{"selector":"a","stableClasses":["x\u0007"]}]}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"annotationClick","id":"1\n2"}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a‮b"}}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a","tag":"d​iv"}}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"pick","target":{"selector":"a b","text":"line\nbreak"}}""")).isNotNull()
    }

    @Test
    fun rectNumbersAreClampedAndNonFiniteRejected() {
        val hover = InspectorMessage.parse("""{"type":"annotationHover","id":"1","rect":{"x":-1e30,"y":5e9,"w":1,"h":1}}""") as InspectorMessage.AnnotationHover
        assertThat(hover.rect).isEqualTo(Rect(-1e6, 1e6, 1.0, 1.0))
        assertThat(InspectorMessage.parse("""{"type":"annotationHover","id":"1","rect":{"x":1e999,"y":0,"w":1,"h":1}}""")).isNull()
    }

    @Test
    fun oversizedMessagesAndModuleErrors() {
        assertThat(InspectorMessage.parse("""{"type":"ready","title":"${"a".repeat(InspectorMessage.MAX_MESSAGE_CHARS)}"}""")).isNull()
        assertThat(InspectorMessage.parse("""{"type":"moduleError","module":"m","message":"boom"}""")).isEqualTo(InspectorMessage.ModuleError("m", "boom"))
    }
}
