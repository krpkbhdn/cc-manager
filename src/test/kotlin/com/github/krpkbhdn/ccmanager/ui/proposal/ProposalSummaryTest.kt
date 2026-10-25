package com.github.krpkbhdn.ccmanager.ui.proposal

import com.github.krpkbhdn.ccmanager.claude.proposal.FormProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PlanProposal
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ProposalSummaryTest {
    private val mapper = JsonMapper.builder().build()

    private fun json(text: String) = mapper.readTree(text)

    private fun permission(tool: String, input: String) =
        PermissionProposal("p", "s", Instant.EPOCH, "", tool, json(input), emptyList())

    @Test
    fun bashShowsTheCommandAndItsDescription() {
        val blocks = permissionSummary("Bash", json("""{"command":"ls -la","description":"List files"}"""))

        assertThat(blocks).containsExactly(SummaryBlock(null, "ls -la", true), SummaryBlock("Description", "List files", false))
    }

    @Test
    fun editShowsBothStringsInFull() {
        val long = permissionSummary("Edit", json("""{"file_path":"A.java","old_string":"${"x".repeat(401)}","new_string":"b"}"""))

        assertThat(long.map { it.label }).containsExactly("File", "Replace", "With")
        assertThat(long[1].text).isEqualTo("x".repeat(401))
        assertThat(long[2].text).isEqualTo("b")
    }

    @Test
    fun multiEditShowsEveryReplacement() {
        val blocks = permissionSummary("MultiEdit", json("""{"file_path":"A.java","edits":[{"old_string":"a","new_string":"b"},{"old_string":"c","new_string":"d"}]}"""))

        assertThat(blocks.map { it.label }).containsExactly("File", "Replace", "With", "Replace", "With")
        assertThat(blocks.drop(1).map { it.text }).containsExactly("a", "b", "c", "d")
    }

    @Test
    fun writeShowsTheFullContent() {
        val blocks = permissionSummary("Write", json("""{"file_path":"A.java","content":"${"y".repeat(500)}"}"""))

        assertThat(blocks.map { it.label }).containsExactly("File", "Content")
        assertThat(blocks[1].text).isEqualTo("y".repeat(500))
    }

    @Test
    fun shortTextsAreNotCollapsedButLongOnesAre() {
        assertThat(collapsed("a\nb")).isNull()
        assertThat(collapsed((1..13).joinToString("\n"))).isEqualTo((1..12).joinToString("\n") + "\n…")
        assertThat(collapsed("z".repeat(5000))).isEqualTo("z".repeat(2000) + "\n…")
    }

    @Test
    fun notebookEditUsesTheNotebookPath() {
        assertThat(permissionSummary("NotebookEdit", json("""{"notebook_path":"n.ipynb"}""")).first().text).isEqualTo("n.ipynb")
    }

    @Test
    fun mcpToolShowsServerAndToolBeforeTheInput() {
        val blocks = permissionSummary("mcp__github__create_issue", json("""{"title":"Bug"}"""))

        assertThat(blocks[0]).isEqualTo(SummaryBlock("MCP tool", "github / create_issue", false))
        assertThat(blocks[1].text).contains("\"title\"").contains("Bug")
    }

    @Test
    fun unknownToolFallsBackToPrettyJson() {
        assertThat(permissionSummary("Frobnicate", json("""{"a":1}""")).single().mono).isTrue()
    }

    @Test
    fun headlinesNameWhatIsAsked() {
        assertThat(proposalHeadline(permission("Bash", """{"command":"npm test\nnpm run lint"}"""))).isEqualTo("Allow Bash: npm test")
        assertThat(proposalHeadline(PlanProposal("p", "s", Instant.EPOCH, "", mapper.createObjectNode(), "", ""))).isEqualTo("Plan ready for approval")
        assertThat(proposalHeadline(FormProposal("f", "s", Instant.EPOCH, "", "deploy", "", "form", "", "", json("{}")))).isEqualTo("deploy asks for input")
    }
}
