package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpLogicTest {
    private val project = Path.of("C:/work/app")
    private val inProject = ExtensionContext.ForProject(1, "app", project)

    private fun server(name: String, scope: ExtensionScope, approval: McpApproval = McpApproval.NOT_REQUIRED) =
        McpServerConfig(name, scope, "stdio", "npx", approval)

    @Test
    fun stdioJsonCarriesCommandArgumentsAndEnv() {
        val json = serverJson(ServerForm(McpTransport.STDIO, " npx ", "-y \"my pkg\" --flag", pairs = listOf("TOKEN" to "s3cret", " " to "dropped")))

        assertThat(json).isEqualTo("""{"type":"stdio","command":"npx","args":["-y","my pkg","--flag"],"env":{"TOKEN":"s3cret"}}""")
    }

    @Test
    fun httpJsonCarriesUrlAndHeadersAndSkipsEmptyHeaders() {
        val json = serverJson(ServerForm(McpTransport.HTTP, url = " https://mcp.example.com/mcp ", pairs = listOf("Authorization" to "Bearer \"x\"")))

        assertThat(json).isEqualTo("""{"type":"http","url":"https://mcp.example.com/mcp","headers":{"Authorization":"Bearer \"x\""}}""")
        assertThat(serverJson(ServerForm(McpTransport.SSE, url = "https://a.b/sse"))).isEqualTo("""{"type":"sse","url":"https://a.b/sse"}""")
    }

    @Test
    fun splitArgumentsKeepsQuotedPartsTogether() {
        assertThat(splitArguments("a  'b c' \"d e\" \"\" f")).containsExactly("a", "b c", "d e", "", "f")
        assertThat(splitArguments("   ")).isEmpty()
    }

    @Test
    fun nameRuleMirrorsTheBackend() {
        assertThat(nameProblem("github", emptyList())).isNull()
        assertThat(nameProblem("my server:v1.2", emptyList())).isNull()
        assertThat(nameProblem("", emptyList())).isNotNull()
        assertThat(nameProblem("-bad", emptyList())).isNotNull()
        assertThat(nameProblem("bad/name", emptyList())).isNotNull()
        assertThat(nameProblem("github", listOf("github"))).contains("already exists")
    }

    @Test
    fun formNeedsACommandOrAFullUrl() {
        assertThat(formProblem(ServerForm(McpTransport.STDIO, command = "  "))).isNotNull()
        assertThat(formProblem(ServerForm(McpTransport.STDIO, command = "npx"))).isNull()
        assertThat(formProblem(ServerForm(McpTransport.HTTP, url = "mcp.example.com"))).isNotNull()
        assertThat(formProblem(ServerForm(McpTransport.HTTP, url = "https://"))).isNotNull()
        assertThat(formProblem(ServerForm(McpTransport.SSE, url = "http://localhost:3000/sse"))).isNull()
    }

    @Test
    fun pastedJsonMustBeAnObject() {
        assertThat(jsonProblem("""{"type":"http","url":"https://a.b"}""")).isNull()
        assertThat(jsonProblem("[1]")).contains("object")
        assertThat(jsonProblem("{nope")).contains("not valid")
    }

    @Test
    fun maskHidesEnvHeadersAndUrlSecretsButKeepsTheRest() {
        val text = "github:\n  Type: http\n  URL: https://user:pw@host/mcp?key=abc&x=1\n  Headers:\n    Authorization: Bearer abc\n  Environment:\n    TOKEN=abc=def\n  Scope: User"

        val masked = maskSecrets(text)

        assertThat(masked).doesNotContain("abc", "user:pw", "def")
        assertThat(masked).contains("Type: http", "Authorization: $MASK", "TOKEN=$MASK", "x=$MASK", "Scope: User", "host/mcp")
    }

    @Test
    fun masksValuesOfStdioFlagsThatNameASecret() {
        val text = "local:\n  Type: stdio\n  Command: npx\n  Args: -y srv --token abc123 --api-key=def456 --Password pw --verbose"

        val masked = maskSecrets(text)

        assertThat(masked).doesNotContain("abc123", "def456", "pw ")
        assertThat(masked).contains("--token $MASK", "--api-key=$MASK", "--verbose", "-y srv")
    }

    @Test
    fun globalContextListsOnlyUserServersAndPluginServersFromHealth() {
        val health = listOf(McpHealth("plugin:code-review:github", "x", McpStatus.CONNECTED, "ok"), McpHealth("github", "x", McpStatus.CONNECTED, "ok"))

        val groups = mcpGroups(listOf(server("b", ExtensionScope.USER), server("a", ExtensionScope.USER), server("p", ExtensionScope.PROJECT)), health, ExtensionContext.Global)

        assertThat(groups.own.map { it.name }).containsExactly("a", "b")
        assertThat(groups.inherited).isEmpty()
        assertThat(groups.plugins.map { it.pluginName to it.displayName }).containsExactly("code-review" to "github")
    }

    @Test
    fun projectContextOwnsProjectBeforeLocalAndInheritsUserServers() {
        val servers = listOf(server("u", ExtensionScope.USER), server("l", ExtensionScope.LOCAL), server("p", ExtensionScope.PROJECT, McpApproval.PENDING))

        val groups = mcpGroups(servers, emptyList(), inProject)

        assertThat(groups.own.map { it.name }).containsExactly("p", "l")
        assertThat(groups.inherited.map { it.name }).containsExactly("u")
        assertThat(groups.own.first().removable).isTrue()
        assertThat(groups.inherited.first().removable).isFalse()
        assertThat(groups.own.first().approval).isEqualTo(McpApproval.PENDING)
    }

    @Test
    fun pluginServersAreNeverRemovable() {
        val groups = mcpGroups(emptyList(), listOf(McpHealth("plugin:a:b", "x", McpStatus.FAILED, "")), ExtensionContext.Global)

        assertThat(groups.plugins.single().removable).isFalse()
    }
}
