package com.github.krpkbhdn.ccmanager.run;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Turns a command string into a shell invocation; Windows prefers Git Bash because Claude writes bash syntax there. */
@Component
public class RunShell {

    private static final String GIT_BASH_ENV = "CLAUDE_CODE_GIT_BASH_PATH";
    private static final List<String> GIT_EXECUTABLES = List.of("git.exe");
    private static final Path BASH_UNDER_GIT = Path.of("bin", "bash.exe");
    private static final int GIT_ROOT_SEARCH_DEPTH = 2;
    private static final String CMD = "cmd.exe";
    private static final String DEFAULT_UNIX_SHELL = "/bin/sh";

    private final HostEnvironment host;

    public RunShell(HostEnvironment host) {
        this.host = host;
    }

    public List<String> invocation(String command) {
        return host.windows() ? windows(command) : unix(command);
    }

    private List<String> windows(String command) {
        return gitBash()
                .map(bash -> List.of(bash.toString(), "-c", command))
                .orElseGet(() -> List.of(CMD, "/c", command));
    }

    static boolean isCmd(String executable) {
        return CMD.equalsIgnoreCase(executable);
    }

    private Optional<Path> gitBash() {
        var configured = host.env(GIT_BASH_ENV);
        if (configured != null && !configured.isBlank() && host.isFile(Path.of(configured))) {
            return Optional.of(Path.of(configured));
        }
        return host.pathCandidates(GIT_EXECUTABLES).stream()
                .filter(host::isFile)
                .flatMap(git -> bashCandidates(git.getParent()))
                .filter(host::isFile)
                .findFirst();
    }

    /** git.exe sits in Git\cmd or Git\mingw64\bin, bash.exe in Git\bin. */
    private static Stream<Path> bashCandidates(Path gitDirectory) {
        var candidates = new ArrayList<Path>();
        var root = gitDirectory;
        for (var depth = 0; depth < GIT_ROOT_SEARCH_DEPTH && root.getParent() != null; depth++) {
            root = root.getParent();
            candidates.add(root.resolve(BASH_UNDER_GIT));
        }
        return candidates.stream();
    }

    private List<String> unix(String command) {
        var shell = host.env("SHELL");
        return List.of(shell == null || shell.isBlank() ? DEFAULT_UNIX_SHELL : shell, "-lc", command);
    }
}
