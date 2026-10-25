package com.github.krpkbhdn.ccmanager.extension.plugin;

import static com.github.krpkbhdn.ccmanager.extension.Json.text;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Tolerant parser of the plugin CLI output; unknown fields are ignored and unusable entries skipped. */
final class PluginParser {

    private static final Pattern COMPONENT = Pattern.compile("^\\s+(.+?) \\((\\d+)\\)\\s*(.*)$");
    private static final Pattern ALWAYS_ON = Pattern.compile("Always-on:\\s*~(\\d+(?:\\.\\d+)?)(k?)");
    private static final Pattern TRAILING_NOTE = Pattern.compile("\\s*\\([^)]*\\)$");
    private static final String DESCRIPTION = "Description:";
    private static final String INVENTORY = "Component inventory";
    private static final String COST = "Projected token cost";
    private static final int THOUSAND = 1000;

    private PluginParser() {}

    static List<InstalledPlugin> installed(String json) {
        return installed(Json.parse(json));
    }

    private static List<InstalledPlugin> installed(JsonNode array) {
        var plugins = new ArrayList<InstalledPlugin>();
        array.forEach(node -> {
            var scope = ExtensionScope.fromCli(text(node, "scope"));
            var id = text(node, "id");
            if (scope.isPresent() && !id.isEmpty()) {
                var project = text(node, "projectPath");
                plugins.add(new InstalledPlugin(id, text(node, "version"), scope.get(), node.path("enabled").asBoolean(false),
                        Path.of(text(node, "installPath")), project.isEmpty() ? null : Path.of(project)));
            }
        });
        return List.copyOf(plugins);
    }

    static List<AvailablePlugin> available(String json) {
        var plugins = new ArrayList<AvailablePlugin>();
        Json.parse(json).path("available").forEach(node -> {
            var id = text(node, "pluginId");
            if (!id.isEmpty()) {
                plugins.add(new AvailablePlugin(id, text(node, "name"), text(node, "description"),
                        text(node, "marketplaceName"), node.path("installCount").asLong(0)));
            }
        });
        return List.copyOf(plugins);
    }

    static List<Marketplace> marketplaces(String json) {
        var marketplaces = new ArrayList<Marketplace>();
        Json.parse(json).forEach(node -> {
            var name = text(node, "name");
            if (!name.isEmpty()) {
                var kind = text(node, "source");
                marketplaces.add(new Marketplace(name, kind, reference(node), text(node, "installLocation")));
            }
        });
        return List.copyOf(marketplaces);
    }

    private static String reference(JsonNode node) {
        for (var field : List.of("repo", "url", "path")) {
            if (!text(node, field).isEmpty()) {
                return text(node, field);
            }
        }
        return "";
    }

    static PluginDetails details(String output) {
        var description = "";
        var components = new ArrayList<PluginComponent>();
        var tokens = 0;
        var inInventory = false;
        for (var line : output.lines().toList()) {
            var trimmed = line.strip();
            if (trimmed.startsWith(DESCRIPTION)) {
                description = trimmed.substring(DESCRIPTION.length()).strip();
            } else if (trimmed.equals(INVENTORY)) {
                inInventory = true;
            } else if (trimmed.equals(COST)) {
                inInventory = false;
            } else if (inInventory) {
                var matcher = COMPONENT.matcher(line);
                if (matcher.matches()) {
                    components.add(new PluginComponent(matcher.group(1), Integer.parseInt(matcher.group(2)), names(matcher.group(3))));
                }
            } else {
                var matcher = ALWAYS_ON.matcher(trimmed);
                if (matcher.find()) {
                    tokens = (int) Math.round(Double.parseDouble(matcher.group(1)) * (matcher.group(2).isEmpty() ? 1 : THOUSAND));
                }
            }
        }
        return new PluginDetails(description, List.copyOf(components), tokens);
    }

    private static List<String> names(String list) {
        var names = TRAILING_NOTE.matcher(list.strip()).replaceFirst("");
        return names.isBlank() ? List.of() : List.of(names.split("\\s*,\\s*"));
    }

    private static Optional<String> resultJson(String stdout) {
        var whole = stdout.strip();
        if (whole.startsWith("{") && isJson(whole)) {
            return Optional.of(whole);
        }
        return stdout.lines().map(String::strip).filter(line -> line.startsWith("{")).reduce((first, second) -> second);
    }

    private static boolean isJson(String text) {
        try {
            Json.parse(text);
            return true;
        } catch (ExtensionException e) {
            return false;
        }
    }

    /** The result is the whole stdout as one JSON object, else its last JSON object line; a failed run without one yields the plain CLI message. */
    static PluginInstallResult installResult(int exitCode, String stdout, String stderr) {
        var json = resultJson(stdout);
        if (json.isEmpty()) {
            var message = (stderr.isBlank() ? stdout : stderr).strip();
            return new PluginInstallResult(exitCode == 0, message, null);
        }
        var root = Json.parse(json.get());
        var shown = root.path("shownCommand");
        var command = shown.isMissingNode() || shown.isNull() ? null
                : new ShownCommand(shown.path("command").isString() ? text(shown, "command") : shown.toString(), text(shown, "sha256"));
        var message = text(root, "message").isEmpty() ? text(root, "error") : text(root, "message");
        return new PluginInstallResult(exitCode == 0 && command == null, message, command);
    }
}
