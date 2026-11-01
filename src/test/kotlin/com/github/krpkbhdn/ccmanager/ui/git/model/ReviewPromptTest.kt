package com.github.krpkbhdn.ccmanager.ui.git.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewPromptTest {
    private fun comment(path: String, lines: IntRange, snippet: List<String>, text: String, removedOnly: Boolean = false) =
        ReviewComment("id-$path", path, lines, removedOnly, snippet, emptyList(), text)

    @Test
    fun `three comments on three files make one prompt with three numbered blocks`() {
        val prompt = reviewPrompt(listOf(
            comment("src/a/Foo.kt", 42..44, listOf("+x", " y"), "one"),
            comment("src/b/Bar.kt", 7..7, listOf("+z"), "two"),
            comment("src/c/Baz.kt", 10..10, listOf("-q"), "three", removedOnly = true),
        ))

        assertThat(prompt).startsWith("Please address these review comments on the current changes. Fix each one or reply why not.\n\n1. src/a/Foo.kt:42-44\n```diff\n+x\n y\n```\nComment: one\n\n")
        assertThat(prompt).contains("\n\n2. src/b/Bar.kt:7\n```diff\n+z\n```\nComment: two\n\n")
        assertThat(prompt).endsWith("\n\n3. src/c/Baz.kt (removed, old 10)\n```diff\n-q\n```\nComment: three")
    }

    @Test
    fun `long snippet is capped with an ellipsis line`() {
        val long = (1..MAX_SNIPPET_LINES + 5).map { "+l$it" }
        val prompt = reviewPrompt(listOf(comment("A.kt", 1..20, long, "t")))

        assertThat(prompt).contains("+l$MAX_SNIPPET_LINES\n…\n```")
        assertThat(prompt).doesNotContain("+l${MAX_SNIPPET_LINES + 1}\n")
    }

    @Test
    fun `carriage returns and control characters never reach the prompt`() {
        val prompt = reviewPrompt(listOf(comment("A.kt", 1..1, listOf("+a\r", "+b\u001b[0m\tc"), "x\r\ny\u0003z")))

        assertThat(prompt).doesNotContain("\r").doesNotContain("\u001b").doesNotContain("\u0003").doesNotContain("\t")
        assertThat(prompt).contains("+a\n+b [0m c\n").endsWith("Comment: x\ny z")
    }

    @Test
    fun `snippet with a code fence gets a longer fence`() {
        val prompt = reviewPrompt(listOf(comment("A.kt", 1..1, listOf("+````", "+```"), "t")))

        assertThat(prompt).contains("`````diff\n+````\n+```\n`````\nComment: t")
    }
}
