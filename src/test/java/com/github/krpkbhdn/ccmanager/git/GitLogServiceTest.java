package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.git.GitLog.Query;
import com.github.krpkbhdn.ccmanager.git.GitLog.RefKind;
import com.github.krpkbhdn.ccmanager.git.GitLog.Row;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitLogServiceTest {

    private final GitLogService service = new GitLogService(new GitRunner());
    private HistoryTestRepo repo;

    @BeforeEach
    void setUp(@TempDir Path directory) {
        assumeTrue(HistoryTestRepo.gitAvailable());
        repo = HistoryTestRepo.init(directory);
    }

    private static Query all(int skip, int limit) {
        return new Query(null, null, null, null, skip, limit);
    }

    /** main: c1 - c2 - m3 - merge(feature, other); feature: f1 - f2; other: o1 (all forked at c2). */
    private String buildBranchyHistory() {
        repo.commit("a.txt", "1", "c1");
        repo.git("tag", "v1");
        repo.commit("a.txt", "2", "c2");
        repo.git("checkout", "-q", "-b", "feature");
        repo.commit("f.txt", "1", "f1");
        repo.commit("f.txt", "2", "f2");
        repo.git("checkout", "-q", "main");
        repo.git("checkout", "-q", "-b", "other");
        repo.commit("o.txt", "1", "o1");
        repo.git("checkout", "-q", "main");
        repo.commit("m.txt", "1", "m3");
        repo.git("merge", "-q", "--no-ff", "-m", "merge feature", "feature");
        repo.git("merge", "-q", "--no-ff", "-m", "merge other", "other");
        return repo.git("rev-parse", "HEAD");
    }

    @Test
    void emptyRepositoryYieldsAnEmptyPage() {
        var page = service.log(repo.root(), all(0, 10));

        assertThat(page.rows()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void rowsCarryParentsRefsAndAuthorAndEveryLinkReachesItsParent() {
        var head = buildBranchyHistory();

        var rows = service.log(repo.root(), all(0, 50)).rows();

        assertThat(rows).hasSize(8);
        var top = rows.getFirst().commit();
        assertThat(top.hash()).isEqualTo(head);
        assertThat(top.parents()).hasSize(2);
        assertThat(top.authorName()).isEqualTo("Test User");
        assertThat(top.authorEmail()).isEqualTo("test@example.com");
        assertThat(top.subject()).isEqualTo("merge other");
        assertThat(top.refs()).extracting(GitLog.Ref::kind).contains(RefKind.HEAD, RefKind.LOCAL_BRANCH);
        assertThat(top.refs()).extracting(GitLog.Ref::name).contains("main");
        assertThat(rows.stream().flatMap(row -> row.commit().refs().stream()))
                .contains(new GitLog.Ref("v1", RefKind.TAG), new GitLog.Ref("feature", RefKind.LOCAL_BRANCH));
        assertThat(rows.stream().mapToInt(Row::laneCount).max().orElse(0)).isGreaterThanOrEqualTo(2);
        GitGraphLayoutTest.assertLinksReachParents(rows);
    }

    @Test
    void pagesWithCarriedStateReplayedPagesAndTheWholeListAgree() {
        buildBranchyHistory();
        var whole = service.log(repo.root(), all(0, 50)).rows();

        var paged = new ArrayList<Row>();
        GraphState state = null;
        for (var skip = 0; skip < whole.size(); skip += 3) {
            var result = service.log(repo.root(), all(skip, 3), state);
            paged.addAll(result.page().rows());
            assertThat(result.page().hasMore()).isEqualTo(skip + 3 < whole.size());
            state = result.next();
        }
        var replayed = service.log(repo.root(), all(3, 3)).rows();

        assertThat(paged).isEqualTo(whole);
        assertThat(replayed).isEqualTo(whole.subList(3, 6));
    }

    @Test
    void branchFilterAndStashAreRespected() {
        buildBranchyHistory();
        repo.write("a.txt", "dirty");
        repo.git("stash", "push", "-q", "-m", "wip");

        var featureOnly = service.log(repo.root(), new Query("feature", null, null, null, 0, 50)).rows();
        var everything = service.log(repo.root(), all(0, 50)).rows();

        assertThat(featureOnly).hasSize(4);
        assertThat(everything).hasSize(8);
        assertThat(everything).allSatisfy(row -> assertThat(row.commit().subject()).doesNotContain("WIP", "wip"));
    }

    @Test
    void authorAndTextFiltersAreCaseInsensitiveLiteralsWithoutGraph() {
        buildBranchyHistory();
        repo.commit("x.txt", "x", "Fix [bug] (1)");

        var byText = service.log(repo.root(), new Query(null, null, "[BUG]", null, 0, 50)).rows();
        var byAuthor = service.log(repo.root(), new Query(null, "TEST user", null, null, 0, 50)).rows();

        assertThat(byText).extracting(row -> row.commit().subject()).containsExactly("Fix [bug] (1)");
        assertThat(byText.getFirst().links()).isEmpty();
        assertThat(byAuthor).hasSize(9);
    }

    @Test
    void singleFilePathFollowsRenames() {
        repo.commit("old.txt", "line one\nline two\nline three\n", "add old");
        repo.git("mv", "old.txt", "new.txt");
        repo.git("commit", "-q", "-m", "rename");
        repo.commit("new.txt", "line one\nline two\nline three\nline four\n", "edit new");

        var followed = service.log(repo.root(), new Query(null, null, null, "new.txt", 0, 50)).rows();

        assertThat(followed).extracting(row -> row.commit().subject()).containsExactly("edit new", "rename", "add old");
    }

    @Test
    void historyOfAFileWithBracketsDoesNotMatchTheGlobSiblings() {
        repo.commit("[id].tsx", "route\n", "add route");
        repo.commit("i.tsx", "other\n", "unrelated i");

        var rows = service.log(repo.root(), new Query(null, null, null, "[id].tsx", 0, 50)).rows();

        assertThat(rows).extracting(row -> row.commit().subject()).containsExactly("add route");
    }

    @Test
    void multilineBodiesAreKeptAndHugeBodiesAreCapped() {
        repo.write("a.txt", "1");
        repo.git("add", "a.txt");
        repo.git("commit", "-q", "-m", "subject", "-m", "first paragraph\nsecond line");
        repo.write("b.txt", "1");
        repo.git("add", "b.txt");
        repo.git("commit", "-q", "-m", "long", "-m", "x".repeat(5000));

        var rows = service.log(repo.root(), all(0, 10)).rows();

        assertThat(rows.get(1).commit().body()).isEqualTo("first paragraph\nsecond line");
        assertThat(rows.get(0).commit().body()).hasSize(GitLogParser.MAX_BODY_CHARS);
    }

    @Test
    void detailsOfANormalCommitListFilesWithRenamesAndBinaries() {
        repo.commit("old.txt", "line one\nline two\nline three\n", "add old");
        repo.write("blob.bin", "a\u0000b");
        repo.git("add", "blob.bin");
        repo.git("mv", "old.txt", "new.txt");
        repo.git("commit", "-q", "-m", "rename and binary");
        var hash = repo.git("rev-parse", "HEAD");

        var details = service.details(repo.root(), hash);

        assertThat(details.commit().subject()).isEqualTo("rename and binary");
        assertThat(details.files()).extracting(file -> file.path() + ":" + file.change() + ":" + file.binary())
                .containsExactlyInAnyOrder("blob.bin:ADDED:true", "new.txt:RENAMED:false");
    }

    @Test
    void detailsOfTheRootCommitShowEverythingAsAdded() {
        var root = repo.commit("a.txt", "1\n2\n", "root");

        var details = service.details(repo.root(), root);

        assertThat(details.files()).singleElement().satisfies(file -> {
            assertThat(file.change()).isEqualTo(ChangeType.ADDED);
            assertThat(file.additions()).isEqualTo(2);
        });
    }

    @Test
    void detailsOfAMergeCommitDiffAgainstTheFirstParentOnly() {
        var head = buildBranchyHistory();

        var details = service.details(repo.root(), head);

        assertThat(details.files()).extracting(file -> file.path()).containsExactly("o.txt");
        assertThat(details.commit().parents()).hasSize(2);
    }

    @Test
    void optionLikeRevisionsAreRejected() {
        buildBranchyHistory();

        assertThat(List.of("--output=x", "-p")).allSatisfy(bad -> org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.details(repo.root(), bad)).isInstanceOf(GitException.class));
    }
}
