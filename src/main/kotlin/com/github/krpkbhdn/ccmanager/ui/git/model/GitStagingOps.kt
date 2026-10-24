package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest
import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest

private const val HOOK_FAILED_TITLE = "Commit hook failed"

fun GitState.stage(paths: List<String>) = act("Staging") { index.stage(repo, paths) }

fun GitState.unstage(paths: List<String>) = act("Unstaging") { index.unstage(repo, paths) }

fun GitState.stageAll() = act("Staging all") { index.stageAll(repo) }

fun GitState.unstageAll() {
    val staged = changeSections(status?.changes().orEmpty()).staged.map { it.path() }
    if (staged.isNotEmpty()) unstage(staged)
}

fun GitState.requestDiscard(paths: List<String>) {
    val noun = if (paths.size == 1) paths.single() else "${paths.size} files"
    dialog = GitDialog.Confirm(
        ConfirmRequest("Discard changes", "Discard all unstaged changes in $noun? This cannot be undone.", "Discard") { discard(paths) },
    )
}

fun GitState.discard(paths: List<String>) = act("Discarding") { index.discard(repo, paths) }

fun GitState.applyPatch(patch: String, reverse: Boolean) = act("Updating the index") { index.applyToIndex(repo, patch, reverse) }

fun GitState.resolveConflict(path: String, side: ConflictSide) = act("Resolving") { conflicts.resolve(repo, path, side) }

fun GitState.markResolved(path: String) = act("Marking resolved") { conflicts.markResolved(repo, path) }

fun GitState.continueOperation() = act("Continuing") { conflicts.continueOperation(repo) }

fun GitState.skipCommit() = act("Skipping commit") { conflicts.skipCommit(repo) }

fun GitState.requestAbort() {
    val name = status?.operation()?.name?.lowercase()?.replace('_', '-') ?: "operation"
    dialog = GitDialog.Confirm(ConfirmRequest("Abort $name", "Throw away the $name in progress and go back to where you started?", "Abort") { abortOperation() })
}

fun GitState.abortOperation() = act("Aborting") { conflicts.abortOperation(repo) }

/** Commits the index; [thenPush] publishes right after. A hook failure opens its output instead of just a toast. */
fun GitState.commit(request: CommitRequest, thenPush: Boolean = false, onCommitted: () -> Unit = {}) =
    act(
        "Committing",
        handle = ::showHookFailure,
        onSuccess = { hash: String ->
            success("Committed $hash", subjectOf(request.message()))
            onCommitted()
            if (thenPush) push()
        },
    ) { index.commit(repo, request) }

private fun GitState.showHookFailure(e: GitException): Boolean {
    if (e.kind() != GitException.Kind.HOOK_FAILED) return false
    host.notify(NotificationKind.ERROR, HOOK_FAILED_TITLE, e.message.orEmpty())
    dialog = GitDialog.Output(HOOK_FAILED_TITLE, e.output().orEmpty().ifBlank { e.message.orEmpty() })
    return true
}

/** Subject and body of the current HEAD commit, to prefill an amend. */
suspend fun GitState.lastCommitMessage(): String? {
    val head = status?.head() ?: return null
    val commit = guarded("Could not read the last commit") { log.details(repo, head) }?.commit() ?: return null
    return if (commit.body().isNullOrBlank()) commit.subject() else "${commit.subject()}\n\n${commit.body().trim()}"
}
