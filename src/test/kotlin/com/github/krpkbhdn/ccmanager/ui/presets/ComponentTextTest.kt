package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.preset.PresetComponent
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class ComponentTextTest {
    private fun line(kind: PresetComponentKind, name: String, payload: JsonNode) = lineOf(PresetComponent(1, kind, name, payload))

    @Test
    fun `instruction shows the first line of prose without a bullet`() {
        val line = line(PresetComponentKind.INSTRUCTION, "Clean code", PresetPayloads.instruction("## Clean\n\n- Names say intent\n- Small methods"))
        assertThat(line.title).isEqualTo("Clean code")
        assertThat(line.secondary).isEqualTo("Names say intent - Small methods")
    }

    @Test
    fun `instruction skips headings and short or label lines before joining prose`() {
        val markdown = "# Conventions\n\nGit:\n\n- Commits carry no AI attribution\nCode:\n- Tests use JUnit 5 and AssertJ\n- Names say intent\n- Fourth line is left out"
        val line = line(PresetComponentKind.INSTRUCTION, "Conventions", PresetPayloads.instruction(markdown))
        assertThat(line.secondary).isEqualTo("Commits carry no AI attribution - Tests use JUnit 5 and AssertJ - Names say intent")
    }

    @Test
    fun `instruction without prose falls back to its heading`() {
        assertThat(firstTextLine("## Rules\n\nGit:")).isEqualTo("Rules")
    }

    @Test
    fun `skill shows its front matter description or a file count`() {
        val described = line(PresetComponentKind.SKILL, "review", PresetPayloads.skill(mapOf(SKILL_FILE to "---\nname: review\ndescription: \"Reviews a diff.\"\n---\nbody")))
        assertThat(described.secondary).isEqualTo("Reviews a diff.")
        val plain = line(PresetComponentKind.SKILL, "review", PresetPayloads.skill(mapOf(SKILL_FILE to "body", "notes.md" to "n")))
        assertThat(plain.secondary).isEqualTo("2 files")
    }

    @Test
    fun `plugin splits its id into name and marketplace`() {
        val line = line(PresetComponentKind.PLUGIN, "ponytail@claude-plugins-official", PresetPayloads.plugin())
        assertThat(line.title).isEqualTo("ponytail")
        assertThat(line.secondary).isEqualTo("From claude-plugins-official")
    }

    @Test
    fun `mcp server summarises command or url`() {
        assertThat(line(PresetComponentKind.MCP_SERVER, "gh", PresetPayloads.mcp("""{"type":"stdio","command":"npx","args":["-y","srv"]}""")).secondary).isEqualTo("stdio · npx -y srv")
        assertThat(line(PresetComponentKind.MCP_SERVER, "docs", PresetPayloads.mcp("""{"url":"https://h/mcp"}""")).secondary).isEqualTo("http · https://h/mcp")
    }

    @Test
    fun `file counts lines and gitignore additions`() {
        val line = line(PresetComponentKind.FILE, ".jscpd.json", PresetPayloads.file(".jscpd.json", "{\n}\n", listOf("report/")))
        assertThat(line.title).isEqualTo(".jscpd.json")
        assertThat(line.secondary).isEqualTo("2 lines · adds 1 .gitignore line")
    }

    @Test
    fun `settings lists its top level keys`() {
        val line = line(PresetComponentKind.SETTINGS, SETTINGS_NAME, PresetPayloads.settings(parseSettings("""{"permissions":{},"env":{}}""")))
        assertThat(line.title).isEqualTo("Project settings")
        assertThat(line.secondary).isEqualTo("permissions, env")
    }

    @Test
    fun `generated skill markdown quotes the description and reads back`() {
        val markdown = skillMarkdown("review", "Say \"hi\": a\nb", "# Body")
        assertThat(frontmatterField(markdown, "name")).isEqualTo("review")
        assertThat(frontmatterField(markdown, "description")).isEqualTo("Say \"hi\": a b")
        assertThat(markdown).endsWith("# Body\n")
    }

    @Test
    fun `front matter is only read at the top of the file`() {
        assertThat(frontmatterField("text\n---\ndescription: x\n---", "description")).isEmpty()
    }
}
