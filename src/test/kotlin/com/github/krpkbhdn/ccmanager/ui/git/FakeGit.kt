package com.github.krpkbhdn.ccmanager.ui.git

import com.github.krpkbhdn.ccmanager.git.GitRunner
import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.git.GitBranchService
import com.github.krpkbhdn.ccmanager.git.GitConflictService
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitHistoryService
import com.github.krpkbhdn.ccmanager.git.GitIndexService
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.git.GitLogService
import com.github.krpkbhdn.ccmanager.git.GitMisc
import com.github.krpkbhdn.ccmanager.git.GitStashService
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.GitStatusService
import com.github.krpkbhdn.ccmanager.git.GitSyncService
import com.github.krpkbhdn.ccmanager.git.GitWatcher
import com.github.krpkbhdn.ccmanager.ui.git.model.GitBackend
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.nio.file.Path
import java.time.Instant
import java.util.function.Consumer

/** What a fake repository holds; mutated by the fake services so the UI sees realistic results of its actions. */
class FakeRepoData(
    var branch: String? = "main",
    var upstream: String? = "origin/main",
    var ahead: Int = 0,
    var behind: Int = 0,
    var operation: GitStatus.Operation = GitStatus.Operation.NONE,
    val changes: MutableList<GitStatus.Change> = mutableListOf(),
    val unstagedDiffs: MutableMap<String, GitDiff.FileDiff> = mutableMapOf(),
    val stagedDiffs: MutableMap<String, GitDiff.FileDiff> = mutableMapOf(),
    val branches: MutableList<GitBranch> = mutableListOf(),
    var rows: List<GitLog.Row> = emptyList(),
    val stashes: MutableList<GitMisc.Stash> = mutableListOf(),
    val tags: MutableList<GitMisc.Tag> = mutableListOf(),
    val remotes: List<GitMisc.Remote> = listOf(GitMisc.Remote("origin", "git@example.com:acme/app.git", "git@example.com:acme/app.git")),
    var identity: GitMisc.Identity = GitMisc.Identity("Dev User", "dev@acme.dev"),
    var repository: Boolean = true,
    var detailFiles: List<GitDiff.FileDiff> = emptyList(),
) {
    /** One-shot failure thrown by the next mutating call. */
    @Volatile var failure: GitException? = null
    val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val patches: MutableList<Pair<String, Boolean>> = java.util.Collections.synchronizedList(mutableListOf())
    val watchers: MutableList<Runnable> = java.util.Collections.synchronizedList(mutableListOf())
    var progressDelayMillis: Long = 0

    fun status() = if (!repository) GitStatus.notARepository() else GitStatus(
        true, branch, rows.firstOrNull()?.commit()?.hash() ?: "0".repeat(HASH_LENGTH), branch == null, upstream, ahead, behind, operation, stashes.size, changes.toList(),
    )

    fun fire() = watchers.toList().forEach { it.run() }

    fun record(call: String) {
        calls += call
        failure?.let { failure = null; throw it }
    }

    private companion object {
        const val HASH_LENGTH = 40
    }
}

/** A complete fake [GitBackend] over [data]; every service reads and mutates the same in-memory repository. */
class FakeGit(val data: FakeRepoData) {
    val backend = GitBackend(status(), index(), branches(), sync(), log(), stashes(), history(), conflicts(), watcher())

    private fun status() = object : GitStatusService(GitRunner()) {
        override fun status(repo: Path) = data.status()
        override fun diff(repo: Path, path: String, staged: Boolean): GitDiff.FileDiff =
            (if (staged) data.stagedDiffs[path] else data.unstagedDiffs[path] ?: data.stagedDiffs[path])
                ?: GitDiff.FileDiff(path, null, GitDiff.ChangeType.MODIFIED, 0, 0, false, emptyList())
        override fun remotes(repo: Path) = data.remotes
        override fun identity(repo: Path) = data.identity
    }

    private fun index() = object : GitIndexService(GitRunner()) {
        override fun stage(repo: Path, paths: List<String>) = mutate("stage ${paths.joinToString()}") { data.replace(paths) { it.copy(staged = if (it.unstaged() == GitStatus.Kind.UNTRACKED) GitStatus.Kind.ADDED else it.unstaged(), unstaged = GitStatus.Kind.NONE) } }
        override fun stageAll(repo: Path) = mutate("stageAll") { data.replace(data.changes.map { it.path() }) { it.copy(staged = if (it.unstaged() == GitStatus.Kind.UNTRACKED) GitStatus.Kind.ADDED else if (it.hasUnstaged()) it.unstaged() else it.staged(), unstaged = GitStatus.Kind.NONE) } }
        override fun unstage(repo: Path, paths: List<String>) = mutate("unstage ${paths.joinToString()}") { data.replace(paths) { it.copy(unstaged = if (it.staged() == GitStatus.Kind.ADDED) GitStatus.Kind.UNTRACKED else it.staged(), staged = GitStatus.Kind.NONE) } }
        override fun discard(repo: Path, paths: List<String>) = mutate("discard ${paths.joinToString()}") { data.changes.removeAll { it.path() in paths && !it.hasStaged() } }
        override fun applyToIndex(repo: Path, patch: String, reverse: Boolean) = mutate("applyToIndex reverse=$reverse") { data.patches += patch to reverse }
        override fun commit(repo: Path, request: GitMisc.CommitRequest): String {
            data.record("commit ${request.message()}")
            if (data.changes.none { it.hasStaged() } && !request.amend()) throw GitException(GitException.Kind.NOTHING_TO_COMMIT, "nothing to commit", "nothing to commit, working tree clean")
            if (request.message().contains("hook-fail")) throw GitException(GitException.Kind.HOOK_FAILED, "pre-commit hook failed", "pre-commit: lint failed\n  src/Main.java:12 unused import\n  src/Main.java:40 missing semicolon")
            data.changes.removeAll { !it.hasUnstaged() }
            data.changes.replaceAll { it.copy(staged = GitStatus.Kind.NONE) }
            return "c0ffee1"
        }
    }

    private fun branches() = object : GitBranchService(GitRunner()) {
        override fun branches(repo: Path) = data.branches.toList()
        override fun checkout(repo: Path, branch: String, autoStash: Boolean) {
            data.record("checkout $branch autoStash=$autoStash")
            if (!autoStash && data.changes.any { it.hasUnstaged() && it.unstaged() != GitStatus.Kind.UNTRACKED } && branch.contains("dirty")) throw GitException(GitException.Kind.DIRTY_TREE, "Your local changes would be overwritten by checkout", "error: Your local changes to the following files would be overwritten by checkout")
            data.branch = branch.substringAfter('/')
            data.branches.replaceAll { it.copy(current = it.name() == data.branch && !it.remote()) }
        }
        override fun checkoutCommit(repo: Path, commitish: String) = mutate("checkoutCommit $commitish") { data.branch = null }
        override fun create(repo: Path, name: String, startPoint: String, checkout: Boolean) = mutate("create $name from $startPoint checkout=$checkout") {
            data.branches += GitBranch(name, false, checkout, null, 0, 0, startPoint, "Created $name", Instant.now())
            if (checkout) { data.branch = name; data.branches.replaceAll { it.copy(current = it.name() == name) } }
        }
        override fun rename(repo: Path, oldName: String, newName: String) = mutate("rename $oldName $newName") { data.branches.replaceAll { if (it.name() == oldName) it.copy(name = newName) else it } }
        override fun delete(repo: Path, name: String, force: Boolean) = mutate("delete $name force=$force") { data.branches.removeAll { it.name() == name && !it.remote() } }
        override fun deleteRemote(repo: Path, remote: String, name: String) = mutate("deleteRemote $remote/$name") { data.branches.removeAll { it.name() == "$remote/$name" } }
        override fun setUpstream(repo: Path, branch: String, upstream: String) = mutate("setUpstream $branch $upstream") { data.upstream = upstream }
        override fun merge(repo: Path, branch: String, mode: GitMisc.MergeMode) = mutate("merge $branch $mode") {}
        override fun rebase(repo: Path, onto: String) = mutate("rebase $onto") {}
    }

    private fun sync() = object : GitSyncService(GitRunner()) {
        override fun fetch(repo: Path, progress: Consumer<String>?) = stream("fetch", progress, "remote: Enumerating objects: 12, done.", "Receiving objects: 100% (12/12), 4.2 KiB, done.") { }
        override fun pull(repo: Path, mode: GitMisc.PullMode, progress: Consumer<String>?) = stream("pull $mode", progress, "Updating 4f2a1c9..9be03d1", "Fast-forward") { data.behind = 0 }
        override fun push(repo: Path, options: GitMisc.PushOptions, progress: Consumer<String>?) = stream("push $options", progress, "Writing objects: 100% (5/5), 1.1 KiB, done.") {
            data.ahead = 0
            if (options.setUpstream()) data.upstream = "origin/${data.branch}"
        }
    }

    private fun stream(call: String, progress: Consumer<String>?, vararg lines: String, effect: () -> Unit) {
        data.record(call)
        lines.forEach { progress?.accept(it); if (data.progressDelayMillis > 0) Thread.sleep(data.progressDelayMillis) }
        effect()
    }

    private fun log() = object : GitLogService(GitRunner()) {
        override fun log(repo: Path, query: GitLog.Query): GitLog.Page {
            val matching = data.rows.filter { row ->
                val c = row.commit()
                (query.author() == null || c.authorName().contains(query.author(), true)) &&
                    (query.text() == null || c.subject().contains(query.text(), true) || c.hash().startsWith(query.text())) &&
                    (query.branch() == null || query.branch().isEmpty() || c.refs().any { it.name() == query.branch() } || query.branch() == data.branch)
            }
            val page = matching.drop(query.skip()).take(query.limit())
            return GitLog.Page(page, matching.size > query.skip() + page.size)
        }
        override fun details(repo: Path, hash: String): GitLog.Details {
            val commit = data.rows.map { it.commit() }.firstOrNull { it.hash() == hash || hash == "HEAD" } ?: data.rows.first().commit()
            return GitLog.Details(commit, data.detailFiles)
        }
    }

    private fun stashes() = object : GitStashService(GitRunner()) {
        override fun list(repo: Path) = data.stashes.toList()
        override fun push(repo: Path, message: String, includeUntracked: Boolean) = mutate("stash $message untracked=$includeUntracked") {
            data.stashes.add(0, GitMisc.Stash(0, message.ifBlank { "WIP on ${data.branch}" }, data.branch ?: "HEAD", Instant.now()))
            data.changes.clear()
        }
        override fun apply(repo: Path, index: Int) = mutate("stashApply $index") {}
        override fun pop(repo: Path, index: Int) = mutate("stashPop $index") { data.stashes.removeIf { it.index() == index } }
        override fun drop(repo: Path, index: Int) = mutate("stashDrop $index") { data.stashes.removeIf { it.index() == index } }
        override fun show(repo: Path, index: Int) = GitDiff(true, data.detailFiles)
    }

    private fun history() = object : GitHistoryService(GitRunner()) {
        override fun reset(repo: Path, commitish: String, mode: GitMisc.ResetMode) = mutate("reset $commitish $mode") {}
        override fun revert(repo: Path, commitish: String) = mutate("revert $commitish") {}
        override fun cherryPick(repo: Path, commitish: String) = mutate("cherryPick $commitish") {}
        override fun tags(repo: Path) = data.tags.toList()
        override fun createTag(repo: Path, name: String, commitish: String?, message: String) = mutate("tag $name") { data.tags += GitMisc.Tag(name, commitish ?: "HEAD", message, message.isNotBlank()) }
        override fun deleteTag(repo: Path, name: String) = mutate("deleteTag $name") { data.tags.removeIf { it.name() == name } }
    }

    private fun conflicts() = object : GitConflictService(GitRunner()) {
        override fun resolve(repo: Path, path: String, side: GitMisc.ConflictSide) = mutate("resolve $path $side") { data.resolveConflict(path) }
        override fun markResolved(repo: Path, path: String) = mutate("markResolved $path") { data.resolveConflict(path) }
        override fun continueOperation(repo: Path) = mutate("continue") { data.operation = GitStatus.Operation.NONE }
        override fun abortOperation(repo: Path) = mutate("abort") { data.operation = GitStatus.Operation.NONE; data.changes.removeAll { it.conflicted() || it.hasStaged() } }
        override fun skipCommit(repo: Path) = mutate("skip") { data.operation = GitStatus.Operation.NONE }
    }

    private fun watcher() = object : GitWatcher(GitRunner()) {
        override fun subscribe(repo: Path, onChange: Runnable): Runnable {
            data.watchers += onChange
            return Runnable { data.watchers -= onChange }
        }
    }

    private fun mutate(call: String, effect: () -> Unit) {
        data.record(call)
        effect()
    }
}

private fun FakeRepoData.replace(paths: List<String>, transform: (GitStatus.Change) -> GitStatus.Change) {
    changes.replaceAll { if (it.path() in paths) transform(it) else it }
}

private fun FakeRepoData.resolveConflict(path: String) {
    changes.replaceAll { if (it.path() == path) GitStatus.Change(path, null, GitStatus.Kind.MODIFIED, GitStatus.Kind.NONE) else it }
}

fun GitStatus.Change.copy(staged: GitStatus.Kind = this.staged(), unstaged: GitStatus.Kind = this.unstaged()) = GitStatus.Change(path(), oldPath(), staged, unstaged)

fun GitBranch.copy(
    name: String = name(),
    current: Boolean = current(),
) = GitBranch(name, remote(), current, upstream(), ahead(), behind(), commit(), subject(), date())
