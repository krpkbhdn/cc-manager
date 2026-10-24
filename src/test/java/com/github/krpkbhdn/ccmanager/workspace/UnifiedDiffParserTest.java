package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedDiffParserTest {

    private static final int BIG = 100_000;

    private static List<FileDiff> parse(String diff) {
        return UnifiedDiffParser.parse(diff, BIG, BIG);
    }

    @Test
    void parsesModifiedFileWithMultipleHunksAndLineNumbers() {
        var files = parse("""
                diff --git a/src/A.java b/src/A.java
                index 111..222 100644
                --- a/src/A.java
                +++ b/src/A.java
                @@ -1,3 +1,3 @@ class A
                 keep
                -old
                +new
                 tail
                @@ -10,2 +10,3 @@
                 ctx
                +added
                 end
                """);

        var file = files.getFirst();
        assertThat(files).hasSize(1);
        assertThat(file.path()).isEqualTo("src/A.java");
        assertThat(file.change()).isEqualTo(ChangeType.MODIFIED);
        assertThat(file.additions()).isEqualTo(2);
        assertThat(file.deletions()).isEqualTo(1);
        assertThat(file.lines()).extracting(l -> l.kind()).containsExactly(
                LineKind.HUNK, LineKind.CONTEXT, LineKind.REMOVED, LineKind.ADDED, LineKind.CONTEXT,
                LineKind.HUNK, LineKind.CONTEXT, LineKind.ADDED, LineKind.CONTEXT);
        assertThat(file.lines().get(2).oldNumber()).isEqualTo(2);
        assertThat(file.lines().get(2).newNumber()).isNull();
        assertThat(file.lines().get(3).newNumber()).isEqualTo(2);
        assertThat(file.lines().get(7).text()).isEqualTo("added");
        assertThat(file.lines().get(7).newNumber()).isEqualTo(11);
        assertThat(file.lines().get(8).oldNumber()).isEqualTo(11);
        assertThat(file.lines().get(8).newNumber()).isEqualTo(12);
    }

    @Test
    void parsesNewFile() {
        var file = parse("""
                diff --git a/n.txt b/n.txt
                new file mode 100644
                index 0000000..e69de29
                --- /dev/null
                +++ b/n.txt
                @@ -0,0 +1,2 @@
                +one
                +two
                """).getFirst();

        assertThat(file.change()).isEqualTo(ChangeType.ADDED);
        assertThat(file.path()).isEqualTo("n.txt");
        assertThat(file.additions()).isEqualTo(2);
    }

    @Test
    void parsesDeletedFile() {
        var file = parse("""
                diff --git a/gone.txt b/gone.txt
                deleted file mode 100644
                index e69de29..0000000
                --- a/gone.txt
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -one
                -two
                """).getFirst();

        assertThat(file.change()).isEqualTo(ChangeType.DELETED);
        assertThat(file.path()).isEqualTo("gone.txt");
        assertThat(file.deletions()).isEqualTo(2);
    }

    @Test
    void parsesPureRenameAndRenameWithEdit() {
        var files = parse("""
                diff --git a/old name.txt b/new name.txt
                similarity index 100%
                rename from old name.txt
                rename to new name.txt
                diff --git a/x.txt b/y.txt
                similarity index 80%
                rename from x.txt
                rename to y.txt
                index 1..2 100644
                --- a/x.txt
                +++ b/y.txt
                @@ -1 +1 @@
                -a
                +b
                """);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).change()).isEqualTo(ChangeType.RENAMED);
        assertThat(files.get(0).oldPath()).isEqualTo("old name.txt");
        assertThat(files.get(0).path()).isEqualTo("new name.txt");
        assertThat(files.get(1).path()).isEqualTo("y.txt");
        assertThat(files.get(1).additions()).isEqualTo(1);
    }

    @Test
    void marksBinaryFiles() {
        var file = parse("""
                diff --git a/img.png b/img.png
                index 1..2 100644
                Binary files a/img.png and b/img.png differ
                """).getFirst();

        assertThat(file.binary()).isTrue();
        assertThat(file.lines()).extracting(l -> l.kind()).containsExactly(LineKind.META);
    }

    @Test
    void keepsNoNewlineMarkerAsMeta() {
        var file = parse("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -a
                \\ No newline at end of file
                +b
                \\ No newline at end of file
                """).getFirst();

        assertThat(file.lines()).extracting(l -> l.kind()).containsExactly(
                LineKind.HUNK, LineKind.REMOVED, LineKind.META, LineKind.ADDED, LineKind.META);
        assertThat(file.lines().get(2).text()).isEqualTo("\\ No newline at end of file");
        assertThat(file.additions()).isEqualTo(1);
    }

    @Test
    void handlesCrlfLineEndings() {
        var file = parse("diff --git a/a.txt b/a.txt\r\n--- a/a.txt\r\n+++ b/a.txt\r\n@@ -1 +1 @@\r\n-a\r\n+b\r\n").getFirst();

        assertThat(file.lines()).extracting(l -> l.text()).containsExactly("@@ -1 +1 @@", "a", "b");
    }

    @Test
    void contentLinesLookingLikeHeadersStayContent() {
        var file = parse("""
                diff --git a/a.md b/a.md
                --- a/a.md
                +++ b/a.md
                @@ -1,2 +1,2 @@
                --- a/not-a-header
                +++ b/not-a-header
                """).getFirst();

        assertThat(file.deletions()).isEqualTo(1);
        assertThat(file.additions()).isEqualTo(1);
    }

    @Test
    void emptyInputGivesNoFiles() {
        assertThat(parse("")).isEmpty();
    }

    @Test
    void capsLinesPerFileWithTruncationMarker() {
        var body = new StringBuilder("diff --git a/a.txt b/a.txt\n@@ -0,0 +1,10 @@\n");
        for (var i = 0; i < 10; i++) {
            body.append("+line").append(i).append('\n');
        }

        var file = UnifiedDiffParser.parse(body.toString(), 3, BIG).getFirst();

        assertThat(file.additions()).isEqualTo(10);
        assertThat(file.lines()).hasSize(4);
        assertThat(file.lines().getLast().text()).isEqualTo(UnifiedDiffParser.TRUNCATED_MARKER);
        assertThat(file.lines().getLast().kind()).isEqualTo(LineKind.META);
    }

    @Test
    void capsTotalLinesAcrossFiles() {
        var diff = """
                diff --git a/a.txt b/a.txt
                @@ -0,0 +1,2 @@
                +1
                +2
                diff --git a/b.txt b/b.txt
                @@ -0,0 +1,2 @@
                +3
                +4
                """;

        var files = UnifiedDiffParser.parse(diff, BIG, 4);

        assertThat(files).hasSize(2);
        assertThat(files.get(1).lines()).extracting(l -> l.text()).contains(UnifiedDiffParser.TRUNCATED_MARKER);
    }
}
