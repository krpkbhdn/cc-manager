package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest
import com.github.krpkbhdn.ccmanager.git.GitMisc.PushOptions
import com.github.krpkbhdn.ccmanager.git.SecretFiles
import com.github.krpkbhdn.ccmanager.git.WorktreeNames
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestDraft
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import com.github.krpkbhdn.ccmanager.ui.state.ALL_CONFIRM_LINES
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val FALLBACK_BASE = "main"
private const val DEFAULT_REMOTE = "origin"
private val SAFE_BRANCH = Regex("[A-Za-z0-9._/-]+")
private const val DRAFT_FAILED = "Could not prepare the pull request"
internal const val MAX_LISTED_COMMITS = 20
private val DEFAULT_BRANCHES = setOf("develop", "main", "master")
private val REPOSITORY_URL = Regex("""[:/]([^/:\s]+)/([^/\s]+?)(?:\.git)?/?$""")
private const val CREATE_FAILED = "Could not create the pull request"
private const val CHANGED_AFTER_CONFIRM = "The working tree changed after the confirmation; review and create the pull request again"

/** What the pull request dialog edits; [head] and [remote] are shown but fixed. */
data class PullRequestForm(val base: String, val head: String, val remote: String, val title: String, val body: String, val repo: String = "")

/** `owner/name` of a GitHub remote URL (https, ssh or scp style), null when it has none. */
fun githubRepository(url: String?): String? = url?.trim()?.let { REPOSITORY_URL.find(it) }?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }

/** Files whose name looks like a key, credential or env file. */
fun secretLookingFiles(files: List<String>): List<String> = files.filter(SecretFiles::looksSecret)

/** Null when [base] may go into a claude prompt and a `gh` argument: a valid branch name of plain characters only. */
fun safeBase(base: String?): String? = base?.trim()?.takeIf { SAFE_BRANCH.matches(it) && WorktreeNames.branchProblem(it).isEmpty() }

fun defaultBase(mainWorktreeBranch: String?): String = safeBase(mainWorktreeBranch) ?: FALLBACK_BASE

/** True for a remote whose URL names a GitHub repository. */
fun isGithubRemote(url: String?): Boolean = url != null && url.contains("github", ignoreCase = true) && githubRepository(url) != null

/** gh READY only ever follows a check of a linked worktree (see [refreshPullRequest]), so it stands for that rule too. */
fun canCreatePullRequest(branch: String?, availability: GhAvailability?, pullRequest: PullRequest?, githubRemote: Boolean): Boolean =
    githubRemote && branch != null && availability == GhAvailability.READY && pullRequest?.state() != PullRequestState.OPEN

/** The remote `git push` will use: that of the upstream, else the only remote, else origin. */
fun pullRequestRemote(upstream: String?, remoteNames: List<String>): String =
    upstream?.substringBefore('/')?.takeIf { '/' in upstream } ?: remoteNames.singleOrNull() ?: DEFAULT_REMOTE

fun PullRequestForm.problem(): String? = when {
    title.isBlank() -> "Title is empty"
    title.any { it.isISOControl() } -> "Title must be a single line"
    safeBase(base) == null -> "'$base' is not a valid base branch"
    safeBase(head) == null -> "'$head' is not a valid head branch"
    head == base -> "Head and base are the same branch"
    head in DEFAULT_BRANCHES -> "'$head' is a default branch; open the pull request from a feature branch"
    repo.isBlank() -> "Remote '$remote' has no GitHub repository address"
    else -> null
}

/** Everything the confirm dialog promises, in the order it happens; [commits] are those `git push` uploads. */
fun confirmText(form: PullRequestForm, files: List<String>, commits: List<String>): String = buildString {
    var step = 1
    secretLookingFiles(files).takeIf { it.isNotEmpty() }?.let { appendLine("WARNING: these files look like secrets: ${it.joinToString()}") }
    if (files.isNotEmpty()) {
        appendLine("${step++}. git add the ${files.size} listed file(s) and commit them with the message: ${form.title}")
        files.forEach { appendLine("     $it") }
    }
    appendLine("${step++}. git push -u ${form.remote} ${form.head}, uploading ${commits.size} commit(s)${if (commits.size > MAX_LISTED_COMMITS) " (newest $MAX_LISTED_COMMITS shown)" else ""}:")
    commits.take(MAX_LISTED_COMMITS).forEach { appendLine("     $it") }
    appendLine("$step. gh pr create --repo ${form.repo} --base ${form.base} --head ${form.head}")
    appendLine("Title: ${form.title}")
    append("Body:\n${form.body.ifBlank { "(empty)" }}")
}

/** Commit what is still dirty, push with upstream, open the pull request; stops at the first failing step. */
internal fun GitBackend.publishPullRequest(repo: Path, form: PullRequestForm, files: List<String>, confirmedHead: String?, progress: (String) -> Unit): String {
    val current = status.status(repo)
    refuseUnfinishedWork(repo, current)
    if (current.changes().map { it.path() }.toSet() != files.toSet() || current.branch() != form.head || current.head() != confirmedHead) {
        throw GitException(GitException.Kind.OTHER, CHANGED_AFTER_CONFIRM, "")
    }
    if (files.isNotEmpty()) {
        progress("Committing")
        index.stage(repo, files)
        index.commit(repo, CommitRequest(form.title, false, false, false))
    }
    sync.push(repo, PushOptions(false, true, false), progress)
    progress("Creating pull request")
    try {
        return github.create(repo, PullRequestDraft(form.repo, form.base, form.head, form.title, form.body))
    } catch (e: IOException) {
        throw GitException(GitException.Kind.OTHER, e.message ?: CREATE_FAILED, "")
    }
}

/** The toolbar button: drafts title and body in the background (cancel closes the dialog), then opens the editable form. */
fun GitState.startPullRequest() {
    val head = status?.branch() ?: return
    val remoteNames = remotes.map { it.name() }
    val upstream = status?.upstream()
    val taskText = task?.task().orEmpty()
    val job = scope.launch(start = CoroutineStart.LAZY) {
        val drafted = gitCall({ fail(DRAFT_FAILED, it) }) {
            val base = draftBase()
            base to git.github.draft(repo, base, taskText)
        }
        dialog = drafted?.let { (base, text) ->
            val remote = pullRequestRemote(upstream, remoteNames)
            val remoteUrl = remotes.firstOrNull { it.name() == remote }?.let { it.pushUrl() ?: it.fetchUrl() }
            GitDialog.PullRequest(PullRequestForm(base, head, remote, text.title(), text.body(), githubRepository(remoteUrl).orEmpty()))
        }
    }
    dialog = GitDialog.Drafting("Create pull request", job)
    job.start()
}

fun GitState.reviewPullRequest(form: PullRequestForm) {
    if (form.problem() != null) return
    val files = status?.changes().orEmpty().map { it.path() }
    val since = status?.upstream() ?: form.base
    val confirmedHead = status?.head()
    scope.launch {
        val commits = gitCall({ fail(DRAFT_FAILED, it) }) { git.log.outgoing(repo, since, MAX_LISTED_COMMITS + 1) } ?: return@launch
        dialog = GitDialog.Confirm(
            ConfirmRequest("Create pull request", confirmText(form, files, commits), "Create pull request", destructive = false, messageLines = ALL_CONFIRM_LINES) { createPullRequest(form, files, confirmedHead) },
        )
    }
}

private fun GitState.createPullRequest(form: PullRequestForm, files: List<String>, head: String?) =
    act("Creating pull request", onSuccess = { url: String ->
        success("Pull request created", url)
        scope.launch { refreshPullRequest() }
    }) { progress -> publishPullRequest(repo, form, files, head, progress) }
