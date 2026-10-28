package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpServerToolsTest {

    private static final String SECRET_URL = "https://user:pw-secret@example.com/mcp?token=tok-123";

    private final McpService mcp = mock(McpService.class);
    private final ProjectService projects = mock(ProjectService.class);

    private static JsonNode args(String json) {
        return JsonMapper.shared().readTree(json);
    }

    @Test
    void listHidesSecretsInTargets() {
        when(mcp.servers(null)).thenReturn(List.of(
                new McpServerConfig("remote", ExtensionScope.USER, "http", SECRET_URL, McpApproval.NOT_REQUIRED),
                new McpServerConfig("local", ExtensionScope.USER, "stdio", "node server.js --key sk-999", McpApproval.NOT_REQUIRED)));

        var result = new ListMcpServersTool(mcp, projects).call(args("{}"));

        assertThat(result).contains("remote", "https://example.com/…", "local", "node").doesNotContain("pw-secret", "tok-123", "sk-999");
    }

    @Test
    void getReturnsConfigAndHealthWithoutSecrets() {
        when(mcp.servers(null)).thenReturn(List.of(new McpServerConfig("remote", ExtensionScope.USER, "http", SECRET_URL, McpApproval.NOT_REQUIRED)));
        when(mcp.health(null)).thenReturn(List.of(new McpHealth("remote", SECRET_URL, McpStatus.CONNECTED, "Connected")));

        var result = new GetMcpServerTool(mcp, projects).call(args("{\"name\":\"remote\",\"health\":true}"));

        assertThat(result).contains("\"status\":\"CONNECTED\"", "\"scope\":\"user\"", "https://example.com/…").doesNotContain("pw-secret", "tok-123");
    }

    @Test
    void getDoesNotRunTheHealthCheckUnlessAsked() {
        when(mcp.servers(null)).thenReturn(List.of(new McpServerConfig("remote", ExtensionScope.USER, "http", SECRET_URL, McpApproval.NOT_REQUIRED)));

        var result = new GetMcpServerTool(mcp, projects).call(args("{\"name\":\"remote\"}"));

        assertThat(result).doesNotContain("status");
        org.mockito.Mockito.verify(mcp, org.mockito.Mockito.never()).health(any());
    }

    @Test
    void getReportsEveryScopeThatDeclaresTheName() {
        when(mcp.servers(null)).thenReturn(List.of(
                new McpServerConfig("dup", ExtensionScope.USER, "http", "https://a.example/x", McpApproval.NOT_REQUIRED),
                new McpServerConfig("dup", ExtensionScope.PROJECT, "http", "https://b.example/x", McpApproval.APPROVED)));

        var result = new GetMcpServerTool(mcp, projects).call(args("{\"name\":\"dup\"}"));

        assertThat(result).contains("\"scope\":\"user\"", "\"scope\":\"project\"", "a.example", "b.example");
    }

    @Test
    void withAProjectTheDefaultScopeIsLocal() {
        when(projects.find(4)).thenReturn(java.util.Optional.of(new com.github.krpkbhdn.ccmanager.project.Project(4, "demo", java.nio.file.Path.of("demo"), java.time.Instant.EPOCH)));

        new RemoveMcpServerTool(mcp, projects).call(args("{\"name\":\"remote\",\"project\":4}"));
        new AddMcpServerTool(mcp, projects).call(args("{\"name\":\"a\",\"config\":{},\"project\":4}"));

        verify(mcp).remove("remote", ExtensionScope.LOCAL, java.nio.file.Path.of("demo"));
        verify(mcp).add(org.mockito.ArgumentMatchers.eq("a"), any(), org.mockito.ArgumentMatchers.eq(ExtensionScope.LOCAL), org.mockito.ArgumentMatchers.eq(java.nio.file.Path.of("demo")));
    }

    @Test
    void getNeverUsesRawCliDetails() {
        when(mcp.servers(null)).thenReturn(List.of(new McpServerConfig("remote", ExtensionScope.USER, "http", SECRET_URL, McpApproval.NOT_REQUIRED)));
        when(mcp.health(null)).thenReturn(List.of());

        new GetMcpServerTool(mcp, projects).call(args("{\"name\":\"remote\"}"));

        org.mockito.Mockito.verify(mcp, org.mockito.Mockito.never()).details(any(), any());
    }

    @Test
    void getRejectsUnknownServer() {
        when(mcp.servers(null)).thenReturn(List.of());
        when(mcp.health(null)).thenReturn(List.of());

        assertThatThrownBy(() -> new GetMcpServerTool(mcp, projects).call(args("{\"name\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("x");
    }

    @Test
    void addPassesConfigAndDoesNotEchoIt() {
        var result = new AddMcpServerTool(mcp, projects).call(args("{\"name\":\"remote\",\"config\":{\"type\":\"http\",\"url\":\"" + SECRET_URL + "\"}}"));

        verify(mcp).add(org.mockito.ArgumentMatchers.eq("remote"), org.mockito.ArgumentMatchers.contains("tok-123"),
                org.mockito.ArgumentMatchers.eq(ExtensionScope.USER), org.mockito.ArgumentMatchers.isNull());
        assertThat(result).contains("remote", "user").doesNotContain("tok-123", "pw-secret");
    }

    @Test
    void addRejectsNonObjectConfigAndUnknownScope() {
        var tool = new AddMcpServerTool(mcp, projects);

        assertThatThrownBy(() -> tool.call(args("{\"name\":\"a\",\"config\":\"x\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.call(args("{\"name\":\"a\",\"config\":{},\"scope\":\"galaxy\"}"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("galaxy");
    }

    @Test
    void addTurnsExtensionFailureIntoToolError() {
        org.mockito.Mockito.doThrow(new ExtensionException("claude mcp failed")).when(mcp).add(any(), any(), any(), any());

        assertThatThrownBy(() -> new AddMcpServerTool(mcp, projects).call(args("{\"name\":\"a\",\"config\":{}}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("claude mcp failed");
    }

    @Test
    void removeUsesScope() {
        var result = new RemoveMcpServerTool(mcp, projects).call(args("{\"name\":\"remote\"}"));

        verify(mcp).remove("remote", ExtensionScope.USER, null);
        assertThat(result).contains("removed");
    }

    @Test
    void onlyReadToolsAreAutoApproved() {
        assertThat(new ListMcpServersTool(mcp, projects).autoApproved()).isTrue();
        assertThat(new GetMcpServerTool(mcp, projects).autoApproved()).isTrue();
        assertThat(new AddMcpServerTool(mcp, projects).autoApproved()).isFalse();
        assertThat(new RemoveMcpServerTool(mcp, projects).autoApproved()).isFalse();
    }
}
