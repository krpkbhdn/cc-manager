package com.github.krpkbhdn.ccmanager.claude.install;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Runs a command attached to a pseudo terminal, for commands that refuse to run without one (claude mcp login).
 * Interrupting the calling thread, or the timeout, kills the process and its children.
 */
final class PtyCommandRunner {

    private static final int COLUMNS = 1000;
    private static final int ROWS = 40;
    private static final int READ_BUFFER_BYTES = 4096;
    /** Output past this is drained and dropped; the whole text is cleaned on every chunk, so the cap also bounds that work. */
    static final int MAX_RAW_CHARS = 1024 * 1024;
    private static final Duration READER_GRACE = Duration.ofSeconds(2);
    /** Moving the cursor to another line is what separates the lines of a terminal screen once the sequences are gone. */
    private static final Pattern CURSOR_TO_NEW_LINE = Pattern.compile("\\u001B\\[[0-9;]*[HfBEdJ]");
    private static final Pattern TERMINAL_CONTROL =Pattern.compile(
            "\\u001B\\[[0-9;?<>=]*[ -/]*[@-~]|\\u001B\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)|\\u001B[()#][0-9A-Za-z]|\\u001B[=>78]|[\\r\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001A]");

    private PtyCommandRunner() {}

    /** @param onOutput gets the whole output so far, control sequences removed, after every chunk */
    static CommandRunner.Result run(List<String> command, Path directory, Duration timeout, Consumer<String> onOutput) throws IOException {
        var environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        var builder = new PtyProcessBuilder(command.toArray(String[]::new))
                .setUseWinConPty(true)
                .setEnvironment(environment)
                .setInitialColumns(COLUMNS)
                .setInitialRows(ROWS);
        if (directory != null) {
            builder.setDirectory(directory.toString());
        }
        PtyProcess process = builder.start();
        var output = new StringBuilder();
        var reader = Thread.ofVirtual().start(() -> read(process.getInputStream(), output, onOutput));
        try {
            var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                ProcessTree.kill(process);
            }
            reader.join(READER_GRACE);
            return new CommandRunner.Result(finished ? process.exitValue() : -1, snapshot(output), "", !finished);
        } catch (InterruptedException e) {
            ProcessTree.kill(process);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command.getFirst(), e);
        }
    }

    static String clean(String raw) {
        var lines = CURSOR_TO_NEW_LINE.matcher(raw).replaceAll("\n");
        return TERMINAL_CONTROL.matcher(lines).replaceAll("").replaceAll("\n{2,}", "\n");
    }

    static void read(InputStream stream, StringBuilder output, Consumer<String> onOutput) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        var bytes = ByteBuffer.allocate(READ_BUFFER_BYTES);
        var chars = CharBuffer.allocate(READ_BUFFER_BYTES);
        var raw = new StringBuilder();
        try (stream) {
            int count;
            while ((count = stream.read(bytes.array(), bytes.position(), bytes.remaining())) > 0) {
                bytes.position(bytes.position() + count).flip();
                decoder.decode(bytes, chars, false);
                bytes.compact();
                chars.flip();
                int appended = Math.min(chars.remaining(), Math.max(0, MAX_RAW_CHARS - raw.length()));
                raw.append(chars, 0, appended);
                chars.clear();
                if (appended == 0) {
                    continue;
                }
                var text = clean(raw.toString());
                synchronized (output) {
                    output.setLength(0);
                    output.append(text);
                }
                onOutput.accept(text);
            }
        } catch (IOException e) {
            // the process ended; what was read so far is the output
        }
    }

    private static String snapshot(StringBuilder output) {
        synchronized (output) {
            return output.toString();
        }
    }
}
