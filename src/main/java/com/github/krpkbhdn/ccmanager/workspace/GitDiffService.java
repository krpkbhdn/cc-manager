package com.github.krpkbhdn.ccmanager.workspace;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Working-tree changes of a project directory against HEAD, including untracked files. */
@Service
public class GitDiffService {

    static final int MAX_LINES_PER_FILE = 2000;
    static final int MAX_TOTAL_LINES = 20000;
    static final int MAX_UNTRACKED_BYTES = 64 * 1024;
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(15);
    private static final String EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
    private static final Logger log = LoggerFactory.getLogger(GitDiffService.class);

    public GitDiff diff(Path projectDirectory) {
        try {
            if (!isWorkTree(projectDirectory)) {
                return notARepository();
            }
            var tracked = git(projectDirectory, "diff", baseRevision(projectDirectory), "--no-color", "--no-ext-diff", "-M", "--relative");
            var files = new ArrayList<>(UnifiedDiffParser.parse(tracked, MAX_LINES_PER_FILE, MAX_TOTAL_LINES));
            files.addAll(untrackedFiles(projectDirectory, remainingLines(files)));
            return new GitDiff(true, List.copyOf(files));
        } catch (IOException e) {
            log.debug("git diff failed in {}: {}", projectDirectory, e.getMessage());
            return notARepository();
        }
    }

    private static GitDiff notARepository() {
        return new GitDiff(false, List.of());
    }

    private static boolean isWorkTree(Path directory) throws IOException {
        var result = run(directory, "rev-parse", "--is-inside-work-tree");
        return result.succeeded() && result.stdout().strip().equals("true");
    }

    private static String baseRevision(Path directory) throws IOException {
        return run(directory, "rev-parse", "--verify", "--quiet", "HEAD").succeeded() ? "HEAD" : EMPTY_TREE;
    }

    private static List<FileDiff> untrackedFiles(Path directory, int lineBudget) throws IOException {
        var names = git(directory, "ls-files", "--others", "--exclude-standard", "-z");
        var result = new ArrayList<FileDiff>();
        var budget = lineBudget;
        for (var name : names.split("\0")) {
            if (!name.isEmpty()) {
                var file = untrackedFile(directory, name, budget);
                budget -= file.lines().size();
                result.add(file);
            }
        }
        return result;
    }

    private static FileDiff untrackedFile(Path directory, String name, int lineBudget) {
        var content = readHead(directory.resolve(name));
        if (content.isEmpty()) {
            return new FileDiff(name, null, ChangeType.UNTRACKED, 0, 0, false, List.of());
        }
        var bytes = content.get();
        if (isBinary(bytes.data())) {
            var marker = new Line(LineKind.META, "Binary file", null, null);
            return new FileDiff(name, null, ChangeType.UNTRACKED, 0, 0, true, List.of(marker));
        }
        return textFile(name, bytes, Math.min(lineBudget, MAX_LINES_PER_FILE));
    }

    private static FileDiff textFile(String name, HeadOfFile head, int maxLines) {
        var text = new String(head.data(), StandardCharsets.UTF_8);
        var all = text.isEmpty() ? new String[0] : text.split("\\r?\\n", -1);
        var count = all.length > 0 && all[all.length - 1].isEmpty() ? all.length - 1 : all.length;
        var shown = Math.max(0, Math.min(count, maxLines));
        var lines = new ArrayList<Line>();
        for (var i = 0; i < shown; i++) {
            lines.add(new Line(LineKind.ADDED, all[i], null, i + 1));
        }
        if (shown < count || head.cut()) {
            lines.add(new Line(LineKind.META, UnifiedDiffParser.TRUNCATED_MARKER, null, null));
        }
        return new FileDiff(name, null, ChangeType.UNTRACKED, shown, 0, false, List.copyOf(lines));
    }

    private record HeadOfFile(byte[] data, boolean cut) {}

    private static Optional<HeadOfFile> readHead(Path file) {
        try (var in = Files.newInputStream(file)) {
            var data = in.readNBytes(MAX_UNTRACKED_BYTES + 1);
            var cut = data.length > MAX_UNTRACKED_BYTES;
            return Optional.of(new HeadOfFile(cut ? Arrays.copyOf(data, MAX_UNTRACKED_BYTES) : data, cut));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean isBinary(byte[] data) {
        for (var b : data) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static int remainingLines(List<FileDiff> files) {
        return Math.max(0, MAX_TOTAL_LINES - files.stream().mapToInt(file -> file.lines().size()).sum());
    }

    private static String git(Path directory, String... arguments) throws IOException {
        var result = run(directory, arguments);
        if (!result.succeeded()) {
            throw new IOException("git " + arguments[0] + " failed: " + result.stderr().strip());
        }
        return result.stdout();
    }

    private static CommandRunner.Result run(Path directory, String... arguments) throws IOException {
        var command = new ArrayList<>(List.of("git", "-c", "core.quotepath=false"));
        command.addAll(List.of(arguments));
        return CommandRunner.run(command, directory, GIT_TIMEOUT);
    }
}
