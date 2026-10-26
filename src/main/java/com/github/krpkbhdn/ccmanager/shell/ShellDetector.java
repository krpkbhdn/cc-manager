package com.github.krpkbhdn.ccmanager.shell;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Finds the interactive shells installed on this machine, the default one first. */
@Service
public class ShellDetector {

    private static final Path UNIX_SHELLS_FILE = Path.of("/etc/shells");
    private static final List<String> UNIX_LOGIN = List.of("-l");
    private static final List<String> GIT_BASH_LOGIN = List.of("--login", "-i");
    private static final List<String> NOT_INTERACTIVE = List.of("nologin", "false");
    private static final String DEFAULT_SYSTEM_ROOT = "C:\\Windows";
    private static final String DEFAULT_PROGRAM_FILES = "C:\\Program Files";

    private final HostEnvironment host;

    public ShellDetector(HostEnvironment host) {
        this.host = host;
    }

    public List<ShellProfile> detect() {
        var found = host.windows() ? windowsShells() : unixShells();
        var byExecutable = new LinkedHashMap<Path, ShellProfile>();
        found.forEach(shell -> byExecutable.putIfAbsent(host.realPath(shell.executable()), shell));
        return List.copyOf(byExecutable.values());
    }

    private List<ShellProfile> windowsShells() {
        var system32 = Path.of(envOr("SystemRoot", DEFAULT_SYSTEM_ROOT)).resolve("System32");
        var programFiles = Path.of(envOr("ProgramFiles", DEFAULT_PROGRAM_FILES));
        var comSpec = host.env("ComSpec");
        var shells = new ArrayList<ShellProfile>();
        powerShell7(programFiles).ifPresent(path -> shells.add(new ShellProfile("PowerShell", path, List.of())));
        existing(system32.resolve("WindowsPowerShell").resolve("v1.0").resolve("powershell.exe"))
                .ifPresent(path -> shells.add(new ShellProfile("Windows PowerShell", path, List.of())));
        existing(comSpec == null || comSpec.isBlank() ? system32.resolve("cmd.exe") : Path.of(comSpec))
                .ifPresent(path -> shells.add(new ShellProfile("Command Prompt", path, List.of())));
        gitBash(programFiles).ifPresent(path -> shells.add(new ShellProfile("Git Bash", path, GIT_BASH_LOGIN)));
        existing(system32.resolve("wsl.exe")).ifPresent(path -> shells.add(new ShellProfile("WSL", path, List.of())));
        return shells;
    }

    private Optional<Path> powerShell7(Path programFiles) {
        var installed = host.childDirectoriesStartingWith(programFiles.resolve("PowerShell"), "").stream()
                .sorted(Comparator.comparing((Path dir) -> dir.getFileName().toString()).reversed())
                .map(dir -> dir.resolve("pwsh.exe"));
        return Stream.concat(host.pathCandidates(List.of("pwsh.exe")).stream(), installed).filter(host::isFile).findFirst();
    }

    /** Derived from git.exe (Git\cmd or Git\mingw64\bin) because System32\bash.exe is the WSL launcher, not Git Bash. */
    private Optional<Path> gitBash(Path programFiles) {
        var fromGit = host.pathCandidates(List.of("git.exe")).stream()
                .filter(host::isFile)
                .flatMap(git -> Stream.of(ancestor(git, 2), ancestor(git, 3)));
        return Stream.concat(fromGit, Stream.of(programFiles.resolve("Git")))
                .filter(Objects::nonNull)
                .map(root -> root.resolve("bin").resolve("bash.exe"))
                .filter(host::isFile)
                .findFirst();
    }

    private List<ShellProfile> unixShells() {
        var paths = new ArrayList<Path>();
        Optional.ofNullable(host.env("SHELL")).filter(value -> !value.isBlank()).map(Path::of).ifPresent(paths::add);
        host.readLines(UNIX_SHELLS_FILE).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("/"))
                .filter(line -> NOT_INTERACTIVE.stream().noneMatch(line::endsWith))
                .map(Path::of)
                .forEach(paths::add);
        return paths.stream()
                .filter(host::isFile)
                .map(path -> new ShellProfile(path.getFileName().toString(), path, UNIX_LOGIN))
                .toList();
    }

    private static Path ancestor(Path path, int levels) {
        var result = path;
        for (int i = 0; i < levels && result != null; i++) {
            result = result.getParent();
        }
        return result;
    }

    private Optional<Path> existing(Path path) {
        return Optional.of(path).filter(host::isFile);
    }

    private String envOr(String name, String fallback) {
        var value = host.env(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
