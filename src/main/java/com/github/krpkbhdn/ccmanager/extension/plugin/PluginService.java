package com.github.krpkbhdn.ccmanager.extension.plugin;

import com.github.krpkbhdn.ccmanager.extension.ClaudeCli;
import com.github.krpkbhdn.ccmanager.extension.ExtensionProperties;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Plugins and marketplaces through {@code claude plugin}. Project and local scope commands run in the project directory.
 * All methods block and throw {@link ExtensionException} with the CLI's message.
 */
@Service
public class PluginService {

    private final ClaudeCli cli;
    private final ExtensionProperties properties;
    private volatile List<AvailablePlugin> availableCache;

    public PluginService(ClaudeCli cli, ExtensionProperties properties) {
        this.cli = cli;
        this.properties = properties;
    }

    /** Every installation in every scope; project and local entries carry their projectPath. */
    public List<InstalledPlugin> installed() {
        return PluginParser.installed(cli.executeChecked(null, properties.queryTimeout(), List.of("plugin", "list", "--json")));
    }

    /** The marketplace catalogue (thousands of entries), fetched once; {@link #refreshAvailable()} reloads it. */
    public List<AvailablePlugin> available() {
        var cached = availableCache;
        return cached != null ? cached : refreshAvailable();
    }

    public synchronized List<AvailablePlugin> refreshAvailable() {
        var json = cli.executeChecked(null, properties.networkTimeout(), List.of("plugin", "list", "--json", "--available"));
        availableCache = PluginParser.available(json);
        return availableCache;
    }

    public List<Marketplace> marketplaces() {
        return PluginParser.marketplaces(
                cli.executeChecked(null, properties.queryTimeout(), List.of("plugin", "marketplace", "list", "--json")));
    }

    /** Project and local plugins are only known inside their project, so the CLI runs in projectDirectory (null for user scope). */
    public PluginDetails details(String pluginId, Path projectDirectory) {
        return PluginParser.details(cli.executeChecked(projectDirectory, properties.queryTimeout(), List.of("plugin", "details", pluginId)));
    }

    /** First step of an install; never accepts a marketplace command on its own, see {@link PluginInstallResult}. */
    public PluginInstallResult install(String pluginId, ExtensionScope scope, Path projectDirectory) {
        return withCommandCheck("install", pluginId, scope, projectDirectory, List.of());
    }

    /** Second step, only after a person confirmed {@code command}: accepts exactly the command with this sha256. */
    public PluginInstallResult installAccepting(String pluginId, ExtensionScope scope, Path projectDirectory, ShownCommand command) {
        return withCommandCheck("install", pluginId, scope, projectDirectory, acceptance(command));
    }

    /** Like {@link #install}: an update can also be held back until a marketplace command is confirmed. */
    public PluginInstallResult update(String pluginId, ExtensionScope scope, Path projectDirectory) {
        return withCommandCheck("update", pluginId, scope, projectDirectory, List.of());
    }

    public PluginInstallResult updateAccepting(String pluginId, ExtensionScope scope, Path projectDirectory, ShownCommand command) {
        return withCommandCheck("update", pluginId, scope, projectDirectory, acceptance(command));
    }

    private static List<String> acceptance(ShownCommand command) {
        return List.of("--accept-command", command.sha256());
    }

    private PluginInstallResult withCommandCheck(
            String action, String pluginId, ExtensionScope scope, Path projectDirectory, List<String> extra) {
        var arguments = scoped(action, pluginId, scope);
        arguments.addAll(extra);
        var result = cli.execute(scope.workingDirectory(projectDirectory), properties.networkTimeout(), arguments);
        var parsed = PluginParser.installResult(result.exitCode(), result.stdout(), result.stderr());
        if (!parsed.installed() && !parsed.needsConfirmation()) {
            throw ClaudeCli.failure(arguments, result);
        }
        return parsed;
    }

    public void uninstall(String pluginId, ExtensionScope scope, Path projectDirectory) {
        run(scoped("uninstall", pluginId, scope), scope, projectDirectory);
    }

    public void setEnabled(String pluginId, ExtensionScope scope, Path projectDirectory, boolean enabled) {
        run(scoped(enabled ? "enable" : "disable", pluginId, scope), scope, projectDirectory);
    }

    public void addMarketplace(String source, ExtensionScope scope, Path projectDirectory) {
        run(List.of("plugin", "marketplace", "add", source, "--scope", scope.cliValue(), "--json"), scope, projectDirectory);
        availableCache = null;
    }

    public void removeMarketplace(String name) {
        run(List.of("plugin", "marketplace", "remove", name), ExtensionScope.USER, null);
        availableCache = null;
    }

    /** Refreshes one marketplace, or all of them when name is null. */
    public void updateMarketplace(String name) {
        var arguments = new ArrayList<>(List.of("plugin", "marketplace", "update"));
        if (name != null) {
            arguments.add(name);
        }
        run(arguments, ExtensionScope.USER, null);
        availableCache = null;
    }

    private void run(List<String> arguments, ExtensionScope scope, Path projectDirectory) {
        cli.executeChecked(scope.workingDirectory(projectDirectory), properties.networkTimeout(), arguments);
    }

    private static List<String> scoped(String action, String pluginId, ExtensionScope scope) {
        return new ArrayList<>(List.of("plugin", action, pluginId, "--scope", scope.cliValue(), "--json"));
    }

}
