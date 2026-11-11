package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpStateTest {
    private fun server(scope: ExtensionScope, transport: String) = McpServerConfig("s", scope, transport, "t", McpApproval.values()[0])

    @Test
    fun `warns only for project stdio servers`() {
        assertThat(declaresProjectStdio(listOf(server(ExtensionScope.PROJECT, "stdio")))).isTrue()
        assertThat(declaresProjectStdio(listOf(server(ExtensionScope.PROJECT, "http"), server(ExtensionScope.USER, "stdio")))).isFalse()
    }
}
