package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsArgumentTest {

    private static final String ARGV_SOURCE = """
            public class Argv {
                public static void main(String[] a) throws Exception {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), String.join("\\n", java.util.Arrays.copyOfRange(a, 1, a.length)));
                }
            }
            """;

    @Test
    void plainArgumentIsLeftAlone() {
        assertThat(WindowsArgument.quote("--model")).isEqualTo("--model");
    }

    @Test
    void jsonQuotesAreEscapedInsideOneQuotedArgument() {
        assertThat(WindowsArgument.quote("{\"a\": \"b\"}")).isEqualTo("\"{\\\"a\\\": \\\"b\\\"}\"");
    }

    @Test
    void backslashesBeforeQuotesAndAtTheEndAreDoubled() {
        assertThat(WindowsArgument.quote("a\\\"b c\\")).isEqualTo("\"a\\\\\\\"b c\\\\\"");
    }

    @Test
    void cmdShimRejectsMetacharactersButNativeExeQuotesAndPlainArgumentsPass() {
        var shim = "C:\\npm\\claude.CMD";

        WindowsArgument.requireSafeForBatchFile(List.of(shim, "--settings", "C:\\s.json"));
        WindowsArgument.requireSafeForBatchFile(List.of("C:\\bin\\claude.exe", "--", "fix & test"));
        WindowsArgument.requireSafeForBatchFile(List.of(shim, "--agents", "{\"a\":1}"));
        assertThatThrownBy(() -> WindowsArgument.requireSafeForBatchFile(List.of(shim, "--", "fix login & signup")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("claude.exe");
        assertThatThrownBy(() -> WindowsArgument.requireSafeForBatchFile(List.of(shim, "--", "a\nb")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void commandLineAtTheWindowsLimitIsRejectedWithAClearMessage() {
        WindowsArgument.requireFitsCommandLine(new String[] {"x".repeat(32_000)});

        assertThatThrownBy(() -> WindowsArgument.requireFitsCommandLine(new String[] {"x".repeat(32_767)}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32766");
    }

    @Test
    void aBatchFileReceivesAnAmpersandDirectoryAndJsonWithQuotesByteExact(@TempDir Path temp) throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var out = temp.resolve("args.txt");
        var source = Files.writeString(temp.resolve("Argv.java"), ARGV_SOURCE);
        var java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        var script = Files.writeString(temp.resolve("fake.cmd"), "@\"" + java + "\" \"" + source + "\" \"" + out + "\" %*\r\n");
        var directory = temp.resolve("R&D").resolve("app");
        var json = "{\"command\":\"C:\\Program Files\\x\",\"args\":[\"-y\",\"x\\\" y\"]}";

        var process = new ProcessBuilder(script.toString(), "--cwd", WindowsArgument.directoryArgument(script.toString(), directory),
                WindowsArgument.quote(json)).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes());

        assertThat(process.waitFor()).as(output).isZero();
        assertThat(Files.readString(out)).isEqualTo("--cwd\n" + directory + "\n" + json);
    }

    @Test
    void aBatchFileReceivesADriveRootWithItsTrailingBackslash(@TempDir Path temp) throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var out = temp.resolve("args.txt");
        var source = Files.writeString(temp.resolve("Argv.java"), ARGV_SOURCE);
        var java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        var script = Files.writeString(temp.resolve("fake.cmd"), "@\"" + java + "\" \"" + source + "\" \"" + out + "\" %*\r\n");
        var root = temp.getRoot();

        var process = new ProcessBuilder(script.toString(), "--cwd", WindowsArgument.directoryArgument(script.toString(), root))
                .redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes());

        assertThat(process.waitFor()).as(output).isZero();
        assertThat(Files.readString(out)).isEqualTo("--cwd\n" + root);
    }

    @Test
    void directoryIsLeftAloneForAnExecutable() {
        var directory = Path.of("R&D");

        assertThat(WindowsArgument.directoryArgument("C:\\bin\\idea64.exe", directory)).isEqualTo(directory.toString());
        assertThat(WindowsArgument.directoryArgument("C:\\bin\\idea.cmd", directory)).isEqualTo("\"" + directory + "\"");
    }

    @Test
    void newlinesForceQuoting() {
        assertThat(WindowsArgument.quote("x\ny")).isEqualTo("\"x\ny\"");
    }
}
