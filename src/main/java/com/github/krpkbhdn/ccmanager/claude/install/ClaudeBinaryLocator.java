package com.github.krpkbhdn.ccmanager.claude.install;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Where claude may live: PATH, native installer, package-manager bins; any claude.exe beats a .cmd shim. */
@Component
public class ClaudeBinaryLocator {

    private static final String APPDATA = "APPDATA";
    private static final String LOCALAPPDATA = "LOCALAPPDATA";
    private static final String VOLTA_HOME = "VOLTA_HOME";
    private static final String SCOOP = "SCOOP";
    private static final String PNPM_HOME = "PNPM_HOME";
    private static final String BUN_INSTALL = "BUN_INSTALL";
    private static final List<String> WINDOWS_NAMES = List.of("claude.exe", "claude.cmd", "claude.bat");
    private static final List<String> UNIX_NAMES = List.of("claude");
    private static final Path NPM_SHIM_EXE =
            Path.of("node_modules", "@anthropic-ai", "claude-code", "bin", "claude.exe");

    public Optional<Path> find(HostEnvironment host) {
        return candidates(host).stream().filter(host::isFile).findFirst().map(found -> preferNpmExe(host, found));
    }

    /** The npm claude.cmd shim is replaced by the real exe beside it, which has no batch-file argument limits. */
    private static Path preferNpmExe(HostEnvironment host, Path found) {
        if (!host.windows() || !found.getFileName().toString().equalsIgnoreCase("claude.cmd")) {
            return found;
        }
        var exe = found.resolveSibling(NPM_SHIM_EXE);
        return host.isFile(exe) ? exe : found;
    }

    /** Lookup order; a GUI-launched process may have a reduced PATH, so fallbacks matter. */
    public List<Path> candidates(HostEnvironment host) {
        var names = host.windows() ? WINDOWS_NAMES : UNIX_NAMES;
        var nativeDirectory = host.home().resolve(".local").resolve("bin");
        var result = new ArrayList<Path>();
        var fallbacks = fallbackDirectories(host);
        for (var name : names) {
            result.addAll(host.pathCandidates(List.of(name)));
            result.add(nativeDirectory.resolve(name));
            fallbacks.forEach(dir -> result.add(dir.resolve(name)));
        }
        return result.stream().distinct().toList();
    }

    private static List<Path> fallbackDirectories(HostEnvironment host) {
        return host.windows() ? windowsFallbacks(host) : unixFallbacks(host);
    }

    private static List<Path> windowsFallbacks(HostEnvironment host) {
        var home = host.home();
        var result = new ArrayList<Path>();
        addEnvDirectory(result, host, APPDATA, "npm");
        addEnvDirectory(result, host, LOCALAPPDATA, "Microsoft", "WinGet", "Links");
        addEnvDirectory(result, host, VOLTA_HOME, "bin");
        addEnvDirectory(result, host, LOCALAPPDATA, "Volta", "bin");
        addEnvDirectory(result, host, SCOOP, "shims");
        result.add(home.resolve("scoop").resolve("shims"));
        addEnvDirectory(result, host, PNPM_HOME);
        addEnvDirectory(result, host, LOCALAPPDATA, "pnpm");
        addEnvDirectory(result, host, BUN_INSTALL, "bin");
        result.add(home.resolve(".bun").resolve("bin"));
        addEnvDirectory(result, host, LOCALAPPDATA, "Yarn", "bin");
        return result;
    }

    private static List<Path> unixFallbacks(HostEnvironment host) {
        var home = host.home();
        var result = new ArrayList<Path>();
        result.add(Path.of("/usr/local/bin"));
        result.add(Path.of("/opt/homebrew/bin"));
        result.add(home.resolve(".npm-global").resolve("bin"));
        result.add(home.resolve(".volta").resolve("bin"));
        result.add(home.resolve(".bun").resolve("bin"));
        result.add(home.resolve(".yarn").resolve("bin"));
        addEnvDirectory(result, host, PNPM_HOME);
        result.add(home.resolve(".local").resolve("share").resolve("pnpm"));
        result.add(Path.of("/home/linuxbrew/.linuxbrew/bin"));
        return result;
    }

    private static void addEnvDirectory(List<Path> result, HostEnvironment host, String variable, String... children) {
        var value = host.env(variable);
        if (value == null || value.isBlank()) {
            return;
        }
        Path directory;
        try {
            directory = Path.of(value);
        } catch (InvalidPathException e) {
            return;
        }
        if (!directory.isAbsolute()) {
            return;
        }
        for (var child : children) {
            directory = directory.resolve(child);
        }
        result.add(directory);
    }
}
