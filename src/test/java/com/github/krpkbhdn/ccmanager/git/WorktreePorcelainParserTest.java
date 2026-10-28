package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorktreePorcelainParserTest {

    private static final String SAMPLE = """
            worktree /repo
            HEAD aaa111
            branch refs/heads/main

            worktree /repo-wt-x
            HEAD bbb222
            branch refs/heads/feature/x
            locked because reason

            worktree /repo-wt-d
            HEAD ccc333
            detached

            worktree /gone
            HEAD ddd444
            branch refs/heads/gone
            prunable gitdir file points to non-existent location

            worktree /bare.git
            bare
            """;

    @Test
    void parsesMainLinkedDetachedLockedPrunableAndBare() {
        var parsed = WorktreePorcelainParser.parse(SAMPLE);

        assertThat(parsed).hasSize(5);
        assertThat(parsed.get(0)).isEqualTo(new WorktreePorcelainParser.ParsedWorktree(Path.of("/repo"), "aaa111", "main", false, false, false, false));
        assertThat(parsed.get(1).branch()).isEqualTo("feature/x");
        assertThat(parsed.get(1).locked()).isTrue();
        assertThat(parsed.get(2).detached()).isTrue();
        assertThat(parsed.get(2).branch()).isEmpty();
        assertThat(parsed.get(3).prunable()).isTrue();
        assertThat(parsed.get(4).bare()).isTrue();
    }

    @Test
    void toleratesCrlfAndMissingTrailingNewline() {
        var parsed = WorktreePorcelainParser.parse("worktree /a\r\nHEAD 1\r\nbranch refs/heads/m\r\n\r\nworktree /b\r\nHEAD 2\r\ndetached");

        assertThat(parsed).extracting(WorktreePorcelainParser.ParsedWorktree::branch).containsExactly("m", "");
        assertThat(parsed.get(1).detached()).isTrue();
    }

    @Test
    void emptyOutputGivesNoWorktrees() {
        assertThat(WorktreePorcelainParser.parse("")).isEmpty();
    }

    @Test
    void keepsSpacesInPathAndLockedReason() {
        var parsed = WorktreePorcelainParser.parse("worktree /my repo/wt x\nHEAD 1\nbranch refs/heads/a b\nlocked why not\n");

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).path()).isEqualTo(Path.of("/my repo/wt x"));
        assertThat(parsed.get(0).branch()).isEqualTo("a b");
        assertThat(parsed.get(0).locked()).isTrue();
    }

    @Test
    void bareEntryHasNoBranchAndNoHead() {
        var parsed = WorktreePorcelainParser.parse("worktree /bare.git\nbare\n");

        assertThat(parsed.get(0)).isEqualTo(new WorktreePorcelainParser.ParsedWorktree(Path.of("/bare.git"), "", "", false, true, false, false));
    }

    @Test
    void detachedHeadKeepsHashAndHasNoBranch() {
        var parsed = WorktreePorcelainParser.parse("worktree /d\nHEAD abc123\ndetached\n");

        assertThat(parsed.get(0).head()).isEqualTo("abc123");
        assertThat(parsed.get(0).detached()).isTrue();
        assertThat(parsed.get(0).branch()).isEmpty();
    }

    @Test
    void blankOutputGivesNoWorktrees() {
        assertThat(WorktreePorcelainParser.parse("  \n\n")).isEmpty();
    }
}
