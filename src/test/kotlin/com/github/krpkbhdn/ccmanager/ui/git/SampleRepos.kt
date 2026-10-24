package com.github.krpkbhdn.ccmanager.ui.git

import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.git.GitMisc
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.GitStatus.Kind
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import com.github.krpkbhdn.ccmanager.workspace.UnifiedDiffParser
import java.time.Instant

private const val MAX_LINES = 5_000

/** Ready-made fake repositories for previews, screenshots and tests. */
object SampleRepos {
    private fun ago(minutes: Long): Instant = Instant.now().minusSeconds(minutes * 60)

    private fun change(path: String, staged: Kind, unstaged: Kind, oldPath: String? = null) = GitStatus.Change(path, oldPath, staged, unstaged)

    fun parse(diff: String): List<GitDiff.FileDiff> = UnifiedDiffParser.parse(diff.trimIndent(), MAX_LINES, MAX_LINES)

    private val registryUnstaged = parse(
        """
        diff --git a/src/main/java/com/acme/SessionRegistry.java b/src/main/java/com/acme/SessionRegistry.java
        --- a/src/main/java/com/acme/SessionRegistry.java
        +++ b/src/main/java/com/acme/SessionRegistry.java
        @@ -41,9 +41,12 @@ public class SessionRegistry {
             private final Map<String, Session> live = new ConcurrentHashMap<>();

        -    public List<Session> sessions() {
        -        return new ArrayList<>(live.values());
        +    public List<SessionSnapshot> sessions() {
        +        return live.values().stream()
        +                .map(Session::snapshot)
        +                .toList();
             }

        -    // TODO make this thread safe
        +    /** Max urgency over the project's live sessions. */
             public AgentStatus projectStatus(long projectId) {
        @@ -88,6 +91,10 @@ public class SessionRegistry {
                 return status;
             }

        +    public Optional<SessionSnapshot> find(String ccmSessionId) {
        +        return Optional.ofNullable(live.get(ccmSessionId)).map(Session::snapshot);
        +    }
        +
             void markInterrupted(String ccmSessionId) {
                 var session = live.get(ccmSessionId);
                 if (session == null) {
        """,
    ).single()

    private val registryStaged = parse(
        """
        diff --git a/src/main/java/com/acme/SessionRegistry.java b/src/main/java/com/acme/SessionRegistry.java
        --- a/src/main/java/com/acme/SessionRegistry.java
        +++ b/src/main/java/com/acme/SessionRegistry.java
        @@ -12,6 +12,7 @@ import java.util.Map;
         import java.util.Optional;
         import java.util.concurrent.ConcurrentHashMap;
        +import java.util.stream.Stream;

         /** Keeps the live sessions and derives each project's traffic light. */
         @Service
        """,
    ).single()

    private val snapshotAdded = parse(
        """
        diff --git a/src/main/java/com/acme/SessionSnapshot.java b/src/main/java/com/acme/SessionSnapshot.java
        new file mode 100644
        --- /dev/null
        +++ b/src/main/java/com/acme/SessionSnapshot.java
        @@ -0,0 +1,8 @@
        +package com.acme;
        +
        +import java.time.Instant;
        +
        +/** Immutable view of one session, safe to hand to the UI. */
        +public record SessionSnapshot(String ccmSessionId, long projectId, AgentStatus status, Instant lastEvent) {
        +    public boolean needsAction() { return status == AgentStatus.NEEDS_ACTION; }
        +}
        """,
    ).single()

    private val docsStaged = parse(
        """
        diff --git a/docs/architecture.md b/docs/architecture.md
        --- a/docs/architecture.md
        +++ b/docs/architecture.md
        @@ -20,7 +20,8 @@ Packages
         - `claude.hook` receives hook events over loopback.
        -- `claude.session` reduces events to a status.
        +- `claude.session` reduces events to a status and keeps subagents.
        +- `git` runs the git CLI and exposes status, log and history.
         - `notification` turns status changes into notifications.
        """,
    ).single()

    private val gradleUnstaged = parse(
        """
        diff --git a/build.gradle.kts b/build.gradle.kts
        --- a/build.gradle.kts
        +++ b/build.gradle.kts
        @@ -10,7 +10,7 @@ plugins {
         group = "com.github.krpkbhdn"
        -version = "1.0.36"
        +version = "1.0.37"

         java {
        """,
    ).single()

    private val removedTest = parse(
        """
        diff --git a/src/test/java/com/acme/OldRegistryTest.java b/src/test/java/com/acme/OldRegistryTest.java
        deleted file mode 100644
        --- a/src/test/java/com/acme/OldRegistryTest.java
        +++ /dev/null
        @@ -1,5 +0,0 @@
        -class OldRegistryTest {
        -    @Test
        -    void legacy() {}
        -}
        -
        """,
    ).single()

    private val untrackedFile = parse(
        """
        diff --git a/src/main/kotlin/com/acme/ui/GitTab.kt b/src/main/kotlin/com/acme/ui/GitTab.kt
        new file mode 100644
        --- /dev/null
        +++ b/src/main/kotlin/com/acme/ui/GitTab.kt
        @@ -0,0 +1,4 @@
        +@Composable
        +fun GitTab(git: GitState) {
        +    Text(git.status?.branch ?: "no branch")
        +}
        """,
    ).single()

    private val conflictFile = parse(
        """
        diff --git a/src/main/java/com/acme/HookServer.java b/src/main/java/com/acme/HookServer.java
        --- a/src/main/java/com/acme/HookServer.java
        +++ b/src/main/java/com/acme/HookServer.java
        @@ -30,11 +30,17 @@ public class HookServer {
             void start() {
        -<<<<<<< ours
        -        server.bind(port);
        -=======
        -        server.bind(loopback, port);
        ->>>>>>> theirs
        +        server.bind(loopback, port);
        +        log.info("hook server listening on {}", port);
             }
        """,
    ).single()

    private val commitFiles = listOf(registryUnstaged, snapshotAdded, docsStaged, gradleUnstaged)

    private fun branch(name: String, current: Boolean = false, remote: Boolean = false, upstream: String? = null, ahead: Int = 0, behind: Int = 0, subject: String, minutes: Long) =
        GitBranch(name, remote, current, upstream, ahead, behind, "%07x".format(name.hashCode() and 0xfffffff), subject, ago(minutes))

    private fun branches(current: String?): MutableList<GitBranch> = mutableListOf(
        branch("feature/git-module", current == "feature/git-module", upstream = "origin/feature/git-module", ahead = 2, behind = 1, subject = "Add status service", minutes = 10),
        branch("main", current == "main", upstream = "origin/main", subject = "Merge branch 'feature/agents-rail'", minutes = 540),
        branch("feature/ui-polish", upstream = "origin/feature/ui-polish", ahead = 3, subject = "Tighten sidebar rows", minutes = 3_000),
        branch("hotfix/hook-timeout", subject = "Raise hook timeout to 10s", minutes = 12_000),
        branch("release/1.0", upstream = "origin/release/1.0", subject = "Prepare 1.0.0", minutes = 19_000),
        branch("origin/main", remote = true, subject = "Merge branch 'feature/agents-rail'", minutes = 540),
        branch("origin/feature/git-module", remote = true, subject = "Git runner", minutes = 120),
        branch("origin/feature/ui-polish", remote = true, subject = "Tighten sidebar rows", minutes = 3_100),
        branch("origin/release/1.0", remote = true, subject = "Prepare 1.0.0", minutes = 19_000),
        branch("upstream/main", remote = true, subject = "Add notification service", minutes = 24_500),
    )

    private fun history(): List<GitLog.Row> {
        val h = SampleHistory()
        val bohdan = "Bohdan K"
        val anna = "Anna Lee"
        val marco = "Marco Rossi"
        val priya = "Priya N"
        val c1 = h.commit("Initial commit", bohdan, 28_800)
        val c2 = h.commit("Add project registry", bohdan, 28_000, c1)
        val c3 = h.commit("Add session registry", anna, 27_000, c2)
        val c4 = h.commit("Wire hook server", bohdan, 26_000, c3)
        val h1 = h.commit("Hook settings writer", marco, 25_500, c4)
        val h2 = h.commit("Write per-session settings file", marco, 25_000, h1)
        val c5 = h.commit("Add notification service", anna, 24_500, c4)
        val m1 = h.commit("Merge branch 'hooks'", bohdan, 24_000, c5, h2)
        val u1 = h.commit("Compose theme tokens", priya, 23_500, m1)
        val u2 = h.commit("Sidebar and project cards", priya, 22_500, u1)
        val c6 = h.commit("Fix flaky notification test", anna, 22_000, m1)
        val u3 = h.commit("Command palette", priya, 21_000, u2)
        val c7 = h.commit("Bump Spring Boot to 4.1.2", bohdan, 20_500, c6)
        val m2 = h.commit("Merge branch 'ui'", bohdan, 20_000, c7, u3)
        val r1 = h.commit("Prepare 1.0.0", bohdan, 19_000, m2)
        val c8 = h.commit("Terminal: handle lone Esc", anna, 18_000, m2)
        val t1 = h.commit("Terminal selection fix", marco, 17_500, c8)
        val t2 = h.commit("Terminal resize on window change", marco, 17_000, t1)
        val c9 = h.commit("Activity tab", anna, 16_000, c8)
        val m3 = h.commit("Merge branch 'terminal'", bohdan, 15_000, c9, t2)
        val x1 = h.commit("Quick actions bar", priya, 14_500, m3)
        val c10 = h.commit("Docs: protocol notes", anna, 13_000, m3)
        val x2 = h.commit("Quick actions send keys", priya, 12_500, x1)
        val c11 = h.commit("Agents rail", anna, 11_000, c10)
        val c12 = h.commit("Show background agents", anna, 9_800, c11)
        val m4 = h.commit("Merge branch 'feature/agents-rail'", bohdan, 540, c12, x2)
        val hf = h.commit("Raise hook timeout to 10s", marco, 12_000, c4)
        val g1 = h.commit("Add git module contracts", bohdan, 400, m4)
        val u4 = h.commit("Tighten sidebar rows", priya, 3_000, u3)
        val g2 = h.commit("Git runner", marco, 300, g1)
        val g3 = h.commit("Failure classification", marco, 220, g2)
        val ui1 = h.commit("Branch popup", priya, 200, g3)
        val g4 = h.commit("Status service", marco, 10, g3)
        val g5 = h.commit("Log graph lanes", marco, 6, g4, ui1)
        h.ref(g5, "HEAD", GitLog.RefKind.HEAD)
        h.ref(g5, "feature/git-module", GitLog.RefKind.LOCAL_BRANCH)
        h.ref(g2, "origin/feature/git-module", GitLog.RefKind.REMOTE_BRANCH)
        h.ref(m4, "main", GitLog.RefKind.LOCAL_BRANCH)
        h.ref(m4, "origin/main", GitLog.RefKind.REMOTE_BRANCH)
        h.ref(u4, "feature/ui-polish", GitLog.RefKind.LOCAL_BRANCH)
        h.ref(hf, "hotfix/hook-timeout", GitLog.RefKind.LOCAL_BRANCH)
        h.ref(r1, "release/1.0", GitLog.RefKind.LOCAL_BRANCH)
        h.ref(r1, "v1.0.0", GitLog.RefKind.TAG)
        h.ref(c5, "v0.9.0", GitLog.RefKind.TAG)
        return h.rows()
    }

    /** A feature branch with staged, unstaged, untracked and deleted files, two commits ahead and one behind. */
    fun busy() = FakeRepoData(
        branch = "feature/git-module", upstream = "origin/feature/git-module", ahead = 2, behind = 1,
        changes = mutableListOf(
            change("src/main/java/com/acme/SessionRegistry.java", Kind.MODIFIED, Kind.MODIFIED),
            change("src/main/java/com/acme/SessionSnapshot.java", Kind.ADDED, Kind.NONE),
            change("docs/architecture.md", Kind.MODIFIED, Kind.NONE),
            change("build.gradle.kts", Kind.NONE, Kind.MODIFIED),
            change("src/test/java/com/acme/OldRegistryTest.java", Kind.NONE, Kind.DELETED),
            change("src/main/kotlin/com/acme/ui/GitTab.kt", Kind.NONE, Kind.UNTRACKED),
            change("website/logo.png", Kind.NONE, Kind.UNTRACKED),
        ),
        unstagedDiffs = mutableMapOf(
            registryUnstaged.path to registryUnstaged, gradleUnstaged.path to gradleUnstaged, removedTest.path to removedTest,
            untrackedFile.path to untrackedFile,
            "website/logo.png" to GitDiff.FileDiff("website/logo.png", null, GitDiff.ChangeType.UNTRACKED, 0, 0, true, emptyList()),
        ),
        stagedDiffs = mutableMapOf(registryStaged.path to registryStaged, snapshotAdded.path to snapshotAdded, docsStaged.path to docsStaged),
        branches = branches("feature/git-module"),
        rows = history(),
        stashes = mutableListOf(
            GitMisc.Stash(0, "WIP on feature/ui-polish: sidebar spacing", "feature/ui-polish", ago(180)),
            GitMisc.Stash(1, "Experiment with commit graph colours", "main", ago(4_000)),
        ),
        tags = mutableListOf(
            GitMisc.Tag("v1.0.0", "a1b2c3d4", "First public release", true),
            GitMisc.Tag("v0.9.0", "b2c3d4e5", "", false),
            GitMisc.Tag("v0.8.0-rc1", "c3d4e5f6", "Release candidate", true),
        ),
        detailFiles = commitFiles,
    )

    /** A merge stopped on two conflicts, with one file already resolved and staged. */
    fun merging() = busy().apply {
        branch = "main"
        operation = GitStatus.Operation.MERGE
        ahead = 0
        behind = 0
        changes.clear()
        changes += change("src/main/java/com/acme/HookServer.java", Kind.UNMERGED, Kind.UNMERGED)
        changes += change("src/main/java/com/acme/HookSettingsWriter.java", Kind.UNMERGED, Kind.UNMERGED)
        changes += change("docs/architecture.md", Kind.MODIFIED, Kind.NONE)
        unstagedDiffs[conflictFile.path] = conflictFile
    }

    fun clean() = busy().apply { changes.clear() }

    fun noUpstream() = busy().apply { upstream = null; ahead = 0; behind = 0; branch = "feature/new-idea" }

    fun missingIdentity() = busy().apply { identity = GitMisc.Identity(null, null) }

    fun notRepository() = FakeRepoData(repository = false)
}
