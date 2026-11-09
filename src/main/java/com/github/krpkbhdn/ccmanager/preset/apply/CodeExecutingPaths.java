package com.github.krpkbhdn.ccmanager.preset.apply;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Project paths that tools run automatically (hooks, MCP servers, tasks, CI), so a preset file must never write them unconfirmed. */
public final class CodeExecutingPaths {

    private static final Set<String> EXECUTABLE_SETTINGS = Set.of("hooks", "statusLine", "apiKeyHelper", "env", "permissions",
            "awsAuthRefresh", "awsCredentialExport", "otelHeadersHelper", "enableAllProjectMcpServers", "enabledPlugins",
            "extraKnownMarketplaces", "sandbox", "fileSuggestion", "enabledMcpjsonServers", "disableAllHooks", "allowedMcpServers");

    private static final List<String> FILES = List.of(".mcp.json", ".envrc", ".pre-commit-config.yaml", ".gitlab-ci.yml", ".vscode/tasks.json",
            ".vscode/settings.json", ".vscode/launch.json", ".claude/settings.json", ".claude/settings.local.json");
    private static final List<String> FOLDERS = List.of(".claude/hooks/", ".github/workflows/", ".husky/", ".githooks/", ".github/actions/",
            ".devcontainer/", ".circleci/");
    private static final Pattern SHORT_NAME = Pattern.compile("~\\d");

    private CodeExecutingPaths() {}

    /** Top-level keys of a settings fragment that run code or change permissions. */
    public static List<String> executableSettingsKeys(JsonNode fragment) {
        return fragment.propertyNames().stream().filter(EXECUTABLE_SETTINGS::contains).toList();
    }

    /** @param resolved the path {@link ProjectFiles#resolve} returned, so matching sees what is really written. */
    static boolean matches(Path projectDir, Path resolved) {
        var path = projectDir.toAbsolutePath().normalize().relativize(resolved).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        // shortcut: any Windows 8.3 style segment (CLAUDE~1) is flagged, as it may alias a protected name
        return SHORT_NAME.matcher(path).find() || FILES.contains(path) || FOLDERS.stream().anyMatch(path::startsWith) || path.endsWith("/.mcp.json");
    }
}
