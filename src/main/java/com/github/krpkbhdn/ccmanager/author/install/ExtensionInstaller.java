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
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Installs the marketplaces, plugins, skills and MCP servers of a profile scope. One failing item never stops the rest,
 * an existing skill is never overwritten (a differing one is reported) and a plugin that needs a confirmation is left to the Plugins page.
 */
@Service
public class ExtensionInstaller {

    private static final Logger log = LoggerFactory.getLogger(ExtensionInstaller.class);
    private static final String UNREADABLE = "could not read the existing list, nothing was changed";

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
        var knownRead = read("marketplaces", () -> plugins.marketplaces().stream().map(Marketplace::name).collect(Collectors.toSet()));
        if (knownRead.isEmpty()) {
            extensions.marketplaces().forEach(marketplace -> failed.add("marketplace " + marketplace.name() + ": " + UNREADABLE));
            return;
        }
        var known = knownRead.get();
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
        var existingRead = read("installed plugins", plugins::installed);
        if (existingRead.isEmpty()) {
            extensions.plugins().forEach(id -> failed.add("plugin " + id + ": " + UNREADABLE));
            return;
        }
        var existing = existingRead.get();
        for (var id : extensions.plugins()) {
            var label = "plugin " + id;
            if (existing.stream().anyMatch(plugin -> sameInstallation(plugin, id, scope, directory))) {
                present.add(label);
                continue;
            }
            try {
                var result = plugins.install(id, scope, directory);
                (result.needsConfirmation() ? attention : installed).add(label);
            } catch (RuntimeException e) {
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
                    present.add(Files.isDirectory(target) && sameContent(skill, source, target) ? label
                            : label + " (differs from the bundled version, left unchanged)");
                } else {
                    copySkill(skill, source, target);
                    installed.add(label);
                }
            } catch (IOException | RuntimeException e) {
                failed.add(label + ": " + e.getMessage());
            }
        }
    }

    private boolean sameContent(ProfileSkill skill, String source, Path target) throws IOException {
        for (var file : skill.files()) {
            var existing = target.resolve(file);
            try (var in = loader.open(source + "/skills/" + skill.name() + "/" + file)) {
                if (!Files.isRegularFile(existing) || !Arrays.equals(in.readAllBytes(), Files.readAllBytes(existing))) {
                    return false;
                }
            }
        }
        return true;
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
        if (Files.exists(directory)) {
            TreeDeleter.delete(directory);
        }
    }

    private void installMcpServers(ExtensionSet extensions, ExtensionScope scope, Path directory, List<String> installed,
            List<String> present, List<String> failed) {
        if (extensions.mcpServers().isEmpty()) {
            return;
        }
        var knownRead = read("MCP servers", () -> mcp.servers(directory).stream().map(McpServerConfig::name).collect(Collectors.toSet()));
        if (knownRead.isEmpty()) {
            extensions.mcpServers().forEach(server -> failed.add("MCP server " + server.name() + ": " + UNREADABLE));
            return;
        }
        var known = knownRead.get();
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
        return read("MCP health", () -> mcp.health(directory).stream()
                .filter(health -> health.status() == McpStatus.NEEDS_AUTHENTICATION && declared.contains(health.name()))
                .map(McpHealth::name).toList()).orElse(List.of());
    }

    private static void run(String label, List<String> installed, List<String> failed, Runnable action) {
        try {
            action.run();
            installed.add(label);
        } catch (RuntimeException e) {
            failed.add(label + ": " + e.getMessage());
        }
    }

    private static <T> Optional<T> read(String what, Supplier<T> query) {
        try {
            return Optional.of(query.get());
        } catch (ExtensionException e) {
            log.warn("Could not read {}: {}", what, e.getMessage());
            return Optional.empty();
        }
    }
}
