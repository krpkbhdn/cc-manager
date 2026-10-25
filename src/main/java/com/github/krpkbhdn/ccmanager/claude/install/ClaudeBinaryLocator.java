package com.github.krpkbhdn.ccmanager.claude.install;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Lists where the claude binary may live: PATH first, then the native installer and package-manager locations; a native claude.exe anywhere beats a .cmd shim. */
@Component
public class ClaudeBinaryLocator {

    private static final List<String> WINDOWS_NAMES = List.of("claude.exe", "claude.cmd", "claude.bat");
    private static final List<String> UNIX_NAMES = List.of("claude");
    private static final List<Path> UNIX_FALLBACK_DIRECTORIES =
            List.of(Path.of("/usr/local/bin"), Path.of("/opt/homebrew/bin"));

    public Optional<Path> find(HostEnvironment host) {
        return candidates(host).stream().filter(host::isFile).findFirst();
    }

    /** Candidates in lookup order; a GUI-launched process may have a reduced PATH, so fallbacks matter. */
    public List<Path> candidates(HostEnvironment host) {
        var names = host.windows() ? WINDOWS_NAMES : UNIX_NAMES;
        var nativeDirectory = host.home().resolve(".local").resolve("bin");
        var result = new ArrayList<Path>();
        for (var name : names) {
            result.addAll(host.pathCandidates(List.of(name)));
            result.add(nativeDirectory.resolve(name));
        }
        if (!host.windows()) {
            UNIX_FALLBACK_DIRECTORIES.forEach(dir -> result.add(dir.resolve("claude")));
        }
        return result.stream().distinct().toList();
    }
}
