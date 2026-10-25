package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus
import java.nio.file.Path
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer

private const val HEALTH_DELAY_MILLIS = 2_500L
private const val WRITE_DELAY_MILLIS = 700L

/** MCP servers for the preview: user, project and local servers over all transports, one pending .mcp.json server, plugin servers. */
class FakeMcp {
    var healthDelayMillis = HEALTH_DELAY_MILLIS
    private val configured = mutableListOf(
        McpServerConfig("github", ExtensionScope.USER, "http", "https://api.githubcopilot.com/mcp/", McpApproval.NOT_REQUIRED),
        McpServerConfig("context7", ExtensionScope.USER, "stdio", "npx", McpApproval.NOT_REQUIRED),
        McpServerConfig("playwright", ExtensionScope.USER, "stdio", "npx", McpApproval.NOT_REQUIRED),
        McpServerConfig("sentry", ExtensionScope.USER, "sse", "https://mcp.sentry.dev/sse", McpApproval.NOT_REQUIRED),
        McpServerConfig("notion", ExtensionScope.USER, "http", "https://mcp.notion.com/mcp?workspace=***", McpApproval.NOT_REQUIRED),
        McpServerConfig("postgres-readonly", ExtensionScope.USER, "stdio", "uvx", McpApproval.NOT_REQUIRED),
        McpServerConfig("docs-search", ExtensionScope.PROJECT, "stdio", "node", McpApproval.APPROVED),
        McpServerConfig("linear", ExtensionScope.PROJECT, "http", "https://mcp.linear.app/mcp", McpApproval.PENDING),
        McpServerConfig("figma", ExtensionScope.PROJECT, "sse", "https://mcp.figma.com/sse", McpApproval.DISABLED),
        McpServerConfig("dev-db", ExtensionScope.LOCAL, "stdio", "docker", McpApproval.NOT_REQUIRED),
    )
    private val statuses = mapOf(
        "github" to (McpStatus.CONNECTED to "Connected"),
        "context7" to (McpStatus.CONNECTED to "Connected"),
        "playwright" to (McpStatus.CONNECTED to "Connected"),
        "sentry" to (McpStatus.NEEDS_AUTHENTICATION to "Needs authentication"),
        "notion" to (McpStatus.NEEDS_AUTHENTICATION to "Needs authentication"),
        "postgres-readonly" to (McpStatus.FAILED to "Failed to connect"),
        "docs-search" to (McpStatus.CONNECTED to "Connected"),
        "linear" to (McpStatus.PENDING to "Pending approval"),
        "figma" to (McpStatus.UNKNOWN to "Disabled"),
        "dev-db" to (McpStatus.FAILED to "Failed to connect"),
    )
    private val pluginServers = listOf(
        McpHealth("plugin:frontend-design:preview", "npx -y @frontend-design/preview-mcp", McpStatus.CONNECTED, "Connected"),
        McpHealth("plugin:code-review:github", "https://api.githubcopilot.com/mcp/", McpStatus.NEEDS_AUTHENTICATION, "Needs authentication"),
        McpHealth("plugin:postgres-tools:pg", "uvx postgres-mcp", McpStatus.FAILED, "Failed to connect"),
    )

    val service: McpService = mock(McpService::class.java).also { mock ->
        `when`(mock.servers(any())).thenAnswer { visible(it.getArgument(0)) }
        `when`(mock.health(any())).thenAnswer { Thread.sleep(healthDelayMillis); health(it.getArgument(0)) }
        `when`(mock.details(anyString(), any())).thenAnswer { details(it.getArgument(0)) }
        doAnswer { Thread.sleep(WRITE_DELAY_MILLIS); added(it.getArgument(0), it.getArgument(1), it.getArgument(2)) }.`when`(mock).add(anyString(), anyString(), any(), any())
        doAnswer { Thread.sleep(WRITE_DELAY_MILLIS); configured.removeIf { s -> s.name == it.getArgument<String>(0) && s.scope == it.getArgument(1) } }.`when`(mock).remove(anyString(), any(), any())
    }

    private fun visible(project: Path?) = configured.filter { project != null || it.scope == ExtensionScope.USER }

    private fun health(project: Path?) = visible(project).map { server ->
        val (status, text) = statuses[server.name] ?: (McpStatus.CONNECTED to "Connected")
        McpHealth(server.name, server.target, status, text)
    } + pluginServers

    private fun added(name: String, json: String, scope: ExtensionScope) {
        val type = Regex("\"type\"\\s*:\\s*\"(\\w+)\"").find(json)?.groupValues?.get(1) ?: "stdio"
        val target = Regex("\"(?:url|command)\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1).orEmpty()
        configured += McpServerConfig(name, scope, type, target, McpApproval.NOT_REQUIRED)
    }

    private fun details(name: String): String = when (name) {
        "github" -> "github:\n  Scope: User config (available in all your projects)\n  Status: Connected\n  Type: http\n  URL: https://api.githubcopilot.com/mcp/\n  Headers:\n    Authorization: Bearer ghp_4f8a91c2d77e0b35a6c1\n    X-MCP-Toolsets: repos,issues,pull_requests\n\nTo remove this server, run: claude mcp remove \"github\" -s user"
        "context7" -> "context7:\n  Scope: User config (available in all your projects)\n  Status: Connected\n  Type: stdio\n  Command: npx\n  Args: -y @upstash/context7-mcp\n  Environment:\n    CONTEXT7_API_KEY=ctx7sk-82b1f5e0-4c1d-47aa-9d5e\n\nTo remove this server, run: claude mcp remove \"context7\" -s user"
        "sentry" -> "sentry:\n  Scope: User config (available in all your projects)\n  Status: Needs authentication\n  Type: sse\n  URL: https://mcp.sentry.dev/sse\n\nTo remove this server, run: claude mcp remove \"sentry\" -s user"
        else -> "$name:\n  Status: Connected\n  Type: stdio\n  Command: node\n  Args: server.js --port 7421\n  Environment:\n    API_TOKEN=tok_91ac03e7f1\n"
    }
}
