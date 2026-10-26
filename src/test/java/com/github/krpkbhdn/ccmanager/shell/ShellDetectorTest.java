package com.github.krpkbhdn.ccmanager.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShellDetectorTest {

    private static final Path ROOT = Path.of("fake-root");
    private static final Path SYSTEM_ROOT = ROOT.resolve("Windows");
    private static final Path SYSTEM32 = SYSTEM_ROOT.resolve("System32");
    private static final Path PROGRAM_FILES = ROOT.resolve("Program Files");
    private static final Path CMD = SYSTEM32.resolve("cmd.exe");
    private static final Path POWERSHELL_5 = SYSTEM32.resolve("WindowsPowerShell").resolve("v1.0").resolve("powershell.exe");

    private FakeHostEnvironment windows() {
        return new FakeHostEnvironment(OperatingSystem.WINDOWS, ROOT.resolve("home"))
                .withEnv("SystemRoot", SYSTEM_ROOT.toString())
                .withEnv("ProgramFiles", PROGRAM_FILES.toString())
                .withEnv("ComSpec", CMD.toString());
    }

    private static List<String> names(List<ShellProfile> shells) {
        return shells.stream().map(ShellProfile::name).toList();
    }

    @Test
    void windowsListsEveryInstalledShellInDefaultOrder() {
        var pwsh = PROGRAM_FILES.resolve("PowerShell").resolve("7").resolve("pwsh.exe");
        var gitCmd = PROGRAM_FILES.resolve("Git").resolve("cmd");
        var host = windows().withFile(pwsh).withFile(POWERSHELL_5).withFile(CMD)
                .withFile(gitCmd.resolve("git.exe")).withFile(PROGRAM_FILES.resolve("Git").resolve("bin").resolve("bash.exe"))
                .withFile(SYSTEM32.resolve("wsl.exe")).withPath(gitCmd);

        var shells = new ShellDetector(host).detect();

        assertThat(names(shells)).containsExactly("PowerShell", "Windows PowerShell", "Command Prompt", "Git Bash", "WSL");
        assertThat(shells.get(0).executable()).isEqualTo(pwsh);
    }

    @Test
    void gitBashIsDerivedFromGitOnPathAndStartedAsLoginShell() {
        var gitRoot = ROOT.resolve("tools").resolve("Git");
        var bash = gitRoot.resolve("bin").resolve("bash.exe");
        var host = windows().withFile(gitRoot.resolve("cmd").resolve("git.exe")).withFile(bash).withPath(gitRoot.resolve("cmd"));

        var gitBash = new ShellDetector(host).detect().getFirst();

        assertThat(gitBash.name()).isEqualTo("Git Bash");
        assertThat(gitBash.executable()).isEqualTo(bash);
        assertThat(gitBash.arguments()).containsExactly("--login", "-i");
    }

    @Test
    void system32BashIsNeverTakenForGitBash() {
        var host = windows().withFile(SYSTEM32.resolve("bash.exe"));

        assertThat(names(new ShellDetector(host).detect())).doesNotContain("Git Bash");
    }

    @Test
    void windowsSkipsShellsThatAreNotInstalled() {
        var host = windows().withFile(CMD);

        assertThat(names(new ShellDetector(host).detect())).containsExactly("Command Prompt");
    }

    @Test
    void pwshOnPathIsPreferredOverProgramFiles() {
        var onPath = ROOT.resolve("bin").resolve("pwsh.exe");
        var host = windows().withFile(onPath).withFile(PROGRAM_FILES.resolve("PowerShell").resolve("7").resolve("pwsh.exe")).withPath(ROOT.resolve("bin"));

        assertThat(new ShellDetector(host).detect().getFirst().executable()).isEqualTo(onPath);
    }

    @Test
    void unixPutsTheUserShellFirstAndSkipsDuplicatesAndNonShells() {
        var bash = Path.of("/bin/bash");
        var usrBash = Path.of("/usr/bin/bash");
        var zsh = Path.of("/usr/bin/zsh");
        var nologin = Path.of("/usr/sbin/nologin");
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, Path.of("/home/me"))
                .withEnv("SHELL", zsh.toString())
                .withLines(Path.of("/etc/shells"), "# valid login shells", "/bin/bash", "/usr/bin/bash", "/usr/bin/zsh", "/usr/sbin/nologin", "/bin/missing", "")
                .withFile(bash).withFile(usrBash).withFile(zsh).withFile(nologin)
                .withLink(usrBash, bash);

        var shells = new ShellDetector(host).detect();

        assertThat(names(shells)).containsExactly("zsh", "bash");
        assertThat(shells.getFirst().arguments()).containsExactly("-l");
        assertThat(shells.get(1).executable()).isEqualTo(bash);
    }

    @Test
    void unixWithoutShellVariableFollowsEtcShells() {
        var fish = Path.of("/usr/bin/fish");
        var host = new FakeHostEnvironment(OperatingSystem.MACOS, Path.of("/Users/me"))
                .withLines(Path.of("/etc/shells"), "/usr/bin/fish").withFile(fish);

        assertThat(names(new ShellDetector(host).detect())).containsExactly("fish");
    }
}
