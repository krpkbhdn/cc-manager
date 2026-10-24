package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.git.GitLog.Commit;
import com.github.krpkbhdn.ccmanager.git.GitLog.GraphLink;
import com.github.krpkbhdn.ccmanager.git.GitLog.Row;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitGraphLayoutTest {

    static Commit commit(String hash, String... parents) {
        return new Commit(hash, hash, List.of(parents), "a", "a@x", Instant.EPOCH, hash, "", List.of());
    }

    /** Every parent link must travel along continuing lines and end on the parent's dot. */
    static void assertLinksReachParents(List<Row> rows) {
        for (var i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            for (var k = 0; k < row.commit().parents().size(); k++) {
                assertParentReached(rows, i, row.commit().parents().get(k), row.links().get(k));
            }
        }
    }

    private static void assertParentReached(List<Row> rows, int from, String parent, GraphLink first) {
        var lane = first.toLane();
        for (var r = from + 1; r < rows.size(); r++) {
            if (rows.get(r).commit().hash().equals(parent)) {
                assertThat(rows.get(r).lane()).as("%s reaches %s", rows.get(from).commit().hash(), parent).isEqualTo(lane);
                return;
            }
            var current = lane;
            var next = rows.get(r).links().stream().filter(link -> link.fromLane() == current).findFirst();
            assertThat(next).as("lane %d continues through row %d", current, r).isPresent();
            lane = next.get().toLane();
        }
    }

    @Test
    void linearHistoryStaysOnOneLane() {
        var rows = GitGraphLayout.layout(List.of(commit("c", "b"), commit("b", "a"), commit("a")), null, 3).rows();

        assertThat(rows).extracting(Row::lane).containsOnly(0);
        assertThat(rows).extracting(Row::color).containsOnly(0);
        assertThat(rows.getLast().links()).isEmpty();
        assertLinksReachParents(rows);
    }

    @Test
    void mergeOpensASecondLaneThatIsFreedWhenTheBranchForksOff() {
        var rows = GitGraphLayout.layout(List.of(commit("m", "a2", "b1"), commit("b1", "a1"), commit("a2", "a1"), commit("a1")), null, 4).rows();

        assertThat(rows).extracting(Row::lane).containsExactly(0, 1, 0, 0);
        assertThat(rows.getFirst().links()).containsExactly(new GraphLink(0, 0, 0), new GraphLink(0, 1, 1));
        assertThat(rows.get(1).laneCount()).isEqualTo(2);
        assertThat(rows.get(3).laneCount()).isEqualTo(2);
        assertThat(rows.get(2).links()).contains(new GraphLink(1, 0, rows.get(1).color()));
        assertLinksReachParents(rows);
    }

    @Test
    void convergingLinesAreRedirectedOntoTheDotOfTheSharedParent() {
        var rows = GitGraphLayout.layout(List.of(commit("x", "p"), commit("y", "p"), commit("p")), null, 3).rows();

        assertThat(rows).extracting(Row::lane).containsExactly(0, 1, 0);
        assertThat(rows.get(1).links()).contains(new GraphLink(1, 0, rows.get(1).color()));
        assertLinksReachParents(rows);
    }

    @Test
    void colourIsStableAlongALaneAndCyclesThroughEightSlots() {
        var commits = List.of(commit("t0"), commit("t1"), commit("t2"), commit("t3"), commit("t4"), commit("t5"), commit("t6"),
                commit("t7"), commit("t8"));
        var rows = GitGraphLayout.layout(commits, null, 9).rows();

        assertThat(rows.getLast().color()).isEqualTo(0);
        var chain = GitGraphLayout.layout(List.of(commit("c", "b"), commit("b", "a"), commit("a")), null, 3).rows();
        assertThat(chain).extracting(Row::color).containsOnly(chain.getFirst().color());
    }

    @Test
    void pagesLaidOutWithCarriedStateEqualTheWholeLayout() {
        var commits = List.of(commit("m", "a3", "b2"), commit("b2", "b1"), commit("a3", "a2"), commit("b1", "a1"), commit("a2", "a1"),
                commit("a1", "a0"), commit("a0"));
        var whole = GitGraphLayout.layout(commits, null, commits.size()).rows();

        var first = GitGraphLayout.layout(commits.subList(0, 4), null, 3);
        var second = GitGraphLayout.layout(commits.subList(3, 7), first.stateAfterMarked(), 4);

        var paged = new java.util.ArrayList<>(first.rows().subList(0, 3));
        paged.addAll(second.rows());
        assertThat(paged).isEqualTo(whole);
        assertLinksReachParents(whole);
    }

    @Test
    void octopusMergeGivesEveryParentItsOwnLane() {
        var rows = GitGraphLayout.layout(List.of(commit("o", "a", "b", "c"), commit("a"), commit("b"), commit("c")), null, 4).rows();

        assertThat(rows.getFirst().links()).extracting(GraphLink::toLane).containsExactly(0, 1, 2);
        assertThat(rows.getFirst().laneCount()).isEqualTo(3);
        assertLinksReachParents(rows);
    }

    @Test
    void linearLayoutHasNoLinks() {
        var rows = GitGraphLayout.linear(List.of(commit("b", "a"), commit("a")));

        assertThat(rows).allSatisfy(row -> assertThat(row.links()).isEmpty());
    }
}
