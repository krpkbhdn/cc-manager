package com.github.krpkbhdn.ccmanager.ui.presets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PresetPayloadsTest {
    @Test
    fun `settings must be a json object`() {
        assertThat(settingsProblem("""{"a":1}""")).isNull()
        assertThat(settingsProblem("[1]")).contains("JSON object")
        assertThat(settingsProblem("{oops")).isEqualTo("That is not valid JSON")
        assertThat(settingsProblem("  ")).isNotNull()
    }

    @Test
    fun `file paths must stay relative inside the project`() {
        assertThat(pathProblem("docs/rules.md")).isNull()
        assertThat(pathProblem(".jscpd.json")).isNull()
        assertThat(pathProblem("/etc/passwd")).isNotNull()
        assertThat(pathProblem("C:\\x")).isNotNull()
        assertThat(pathProblem("a/../../b")).isNotNull()
        assertThat(pathProblem("./")).isNotNull()
        assertThat(pathProblem("")).isNotNull()
    }

    @Test
    fun `skill and agent names are folder names`() {
        assertThat(folderNameProblem("code-review_2.1")).isNull()
        assertThat(folderNameProblem("-x")).isNotNull()
        assertThat(folderNameProblem("a b")).isNotNull()
        assertThat(folderNameProblem("a..b")).isNotNull()
    }

    @Test
    fun `gitignore box becomes trimmed non blank lines`() {
        assertThat(parseGitignore(" a/ \n\n  b  \n")).containsExactly("a/", "b")
    }

    @Test
    fun `pretty json round trips with spaced colons`() {
        val node = parseSettings("""{"a":{"b":[1,2]}}""")
        val text = prettyJson(node)
        assertThat(text).contains("\"a\": {")
        assertThat(parseSettings(text)).isEqualTo(node)
    }
}
