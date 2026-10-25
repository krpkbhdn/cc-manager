package com.github.krpkbhdn.ccmanager.run;

import static com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem.LINUX;
import static com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem.WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RunShellTest {

    static final Path HOME = Path.of("home");
    static final Path GIT = Path.of("Git");

    @Test
    void windowsPrefersTheConfiguredGitBash() {
        var configured = Path.of("custom", "bash.exe");
        var host = new FakeHostEnvironment(WINDOWS, HOME).withFile(configured).withEnv("CLAUDE_CODE_GIT_BASH_PATH", configured.toString());

        assertThat(new RunShell(host).invocation("npm start")).containsExactly(configured.toString(), "-c", "npm start");
    }

    @Test
    void windowsDerivesBashFromGitOnPath() {
        var bash = GIT.resolve("bin").resolve("bash.exe");
        var host = new FakeHostEnvironment(WINDOWS, HOME).withFile(GIT.resolve("cmd").resolve("git.exe")).withFile(bash).withPath(GIT.resolve("cmd"));

        assertThat(new RunShell(host).invocation("ls")).containsExactly(bash.toString(), "-c", "ls");
    }

    @Test
    void windowsFindsBashWhenGitSitsInMingwBin() {
        var bash = GIT.resolve("bin").resolve("bash.exe");
        var mingw = GIT.resolve("mingw64").resolve("bin");
        var host = new FakeHostEnvironment(WINDOWS, HOME).withFile(mingw.resolve("git.exe")).withFile(bash).withPath(mingw);

        assertThat(new RunShell(host).invocation("ls").getFirst()).isEqualTo(bash.toString());
    }

    @Test
    void windowsFallsBackToCmd() {
        var host = new FakeHostEnvironment(WINDOWS, HOME);

        assertThat(new RunShell(host).invocation("dir")).containsExactly("cmd.exe", "/c", "dir");
    }

    @Test
    void unixUsesTheLoginShellOrBinSh() {
        assertThat(new RunShell(new FakeHostEnvironment(LINUX, HOME).withEnv("SHELL", "/bin/zsh")).invocation("ls"))
                .containsExactly("/bin/zsh", "-lc", "ls");
        assertThat(new RunShell(new FakeHostEnvironment(LINUX, HOME)).invocation("ls")).containsExactly("/bin/sh", "-lc", "ls");
    }
}
