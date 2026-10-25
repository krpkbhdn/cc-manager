package com.github.krpkbhdn.ccmanager.author.install;

import com.github.krpkbhdn.ccmanager.author.AuthorProfileLoader;
import com.github.krpkbhdn.ccmanager.author.ExtensionSet;
import com.github.krpkbhdn.ccmanager.author.ProfileSkill;
import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus;
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Installs the marketplaces, plugins, skills and MCP servers of a profile scope. One failing item never stops the rest,
 * an existing skill is never overwritten and a plugin that needs a confirmation is left to the Plugins page.
 */
@Service
public class ExtensionInstaller {

    private final PluginService plugins;
    private final McpService mcp;
    private final SkillService skills;
    private final AuthorProfileLoader loader;

    public ExtensionInstaller(PluginService plugins, McpService mcp, SkillService skills, AuthorProfileLoader loader) {
        this.plugins = plugins;
        this.mcp = mcp;
        this.skills = skills;
        this.loader = loader;
    }

    /** @param projectDirectory the project for project or local scope, null for user scope */
    public InstallOutcome install(ExtensionSet extensions, ExtensionScope scope, Path projectDirectory) {
        var installed = new ArrayList<String>();
        var present = new ArrayList<String>();
        var attention = new ArrayList<String>();
        var failed = new ArrayList<String>();
        installMarketplaces(extensions, scope, projectDirectory, installed, present, failed);
        installPlugins(extensions, scope, projectDirectory, installed, present, attention, failed);
        installSkills(extensions, scope, projectDirectory, installed, present, failed);
        installMcpServers(extensions, scope, projectDirectory, installed, present, failed);
        return new InstallOutcome(installed, present, attention, failed, signInNeeded(extensions, projectDirectory));
    }

    private void installMarketplaces(ExtensionSet extensions, ExtensionScope scope, Path directory, List<String> installed,
            List<String> present, List<String> failed) {
        if (extensions.marketplaces().isEmpty()) {
            return;
        }
        var known = attempt(() -> plugins.marketplaces().stream().map(Marketplace::name).collect(Collectors.toSet()), Set.<String>of());
        for (var marketplace : extensions.marketplaces()) {
            var label = "marketplace " + marketplace.name();
            if (known.contains(marketplace.name())) {
                present.add(label);
            } else {
                run(label, installed, failed, () -> plugins.addMarketplace(marketplace.source(), scope, directory));
            }
        }
    }

    private void installPlugins(ExtensionSet extensions, ExtensionScope scope, Path directory, List<String> installed, List<String> present,
            List<String> attention, List<String> failed) {
        if (extensions.plugins().isEmpty()) {
            return;
        }
        var existing = attempt(plugins::installed, List.<InstalledPlugin>of());
        for (var id : extensions.plugins()) {
            var label = "plugin " + id;
            if (existing.stream().anyMatch(plugin -> sameInstallation(plugin, id, scope, directory))) {
                present.add(label);
                continue;
            }
            try {
                var result = plugins.install(id, scope, directory);
                (result.needsConfirmation() ? attention : installed).add(label);
            } catch (ExtensionException e) {
                failed.add(label + ": " + e.getMessage());
            }
        }
    }

    private static boolean sameInstallation(InstalledPlugin plugin, String id, ExtensionScope scope, Path directory) {
        return plugin.id().equals(id) && plugin.scope() == scope
                && (scope == ExtensionScope.USER || Objects.equals(plugin.projectPath(), directory));
    }

    private void installSkills(ExtensionSet extensions, ExtensionScope scope, Path directory, List<String> installed, List<String> present,
            List<String> failed) {
        var source = scope == ExtensionScope.USER ? AuthorProfileLoader.USER_DIRECTORY : AuthorProfileLoader.PROJECT_DIRECTORY;
        for (var skill : extensions.skills()) {
            var label = "skill " + skill.name();
            try {
                var target = skills.skillDirectory(scope, directory, skill.name());
                if (Files.exists(target)) {
                    present.add(label);
                } else {
                    copySkill(skill, source, target);
                    installed.add(label);
                }
            } catch (IOException | RuntimeException e) {
                failed.add(label + ": " + e.getMessage());
            }
        }
    }

    private void copySkill(ProfileSkill skill, String source, Path target) throws IOException {
        var parent = target.getParent();
        Files.createDirectories(parent);
        var staging = Files.createTempDirectory(parent, "." + skill.name() + "-");
        try {
            for (var file : skill.files()) {
                try (var in = loader.open(source + "/skills/" + skill.name() + "/" + file)) {
                    var destination = staging.resolve(file);
                    Files.createDirectories(destination.getParent());
                    Files.copy(in, destination);
                }
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            deleteRecursively(staging);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private void installMcpServers(ExtensionSet extensions, ExtensionScope scope, Path directory, List<String> installed,
            List<String> present, List<String> failed) {
        if (extensions.mcpServers().isEmpty()) {
            return;
        }
        var known = attempt(() -> mcp.servers(directory).stream().map(McpServerConfig::name).collect(Collectors.toSet()), Set.<String>of());
        for (var server : extensions.mcpServers()) {
            var label = "MCP server " + server.name();
            if (known.contains(server.name())) {
                present.add(label);
            } else {
                run(label, installed, failed, () -> mcp.add(server.name(), server.config().toString(), scope, directory));
            }
        }
    }

    private List<String> signInNeeded(ExtensionSet extensions, Path directory) {
        if (extensions.mcpServers().isEmpty()) {
            return List.of();
        }
        var declared = extensions.mcpServers().stream().map(server -> server.name()).collect(Collectors.toSet());
        return attempt(() -> mcp.health(directory).stream()
                .filter(health -> health.status() == McpStatus.NEEDS_AUTHENTICATION && declared.contains(health.name()))
                .map(McpHealth::name).toList(), List.<String>of());
    }

    private static void run(String label, List<String> installed, List<String> failed, Runnable action) {
        try {
            action.run();
            installed.add(label);
        } catch (ExtensionException e) {
            failed.add(label + ": " + e.getMessage());
        }
    }

    private static <T> T attempt(Supplier<T> query, T fallback) {
        try {
            return query.get();
        } catch (ExtensionException e) {
            return fallback;
        }
    }
}
