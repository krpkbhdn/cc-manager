package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.github.CheckSummary
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.GhCli
import com.github.krpkbhdn.ccmanager.github.GhProperties
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestDraft
import com.github.krpkbhdn.ccmanager.git.ChangeDescription
import com.github.krpkbhdn.ccmanager.git.ChangeDescriptionService
import com.github.krpkbhdn.ccmanager.github.PullRequestLookup
import com.github.krpkbhdn.ccmanager.github.PullRequestService
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** What the Git tab needs from the GitHub CLI; calls block, so callers stay off the UI thread. */
interface GitHubPort {
    val pollInterval: Duration

    fun availability(directory: Path): GhAvailability

    /** A failed gh call is [PullRequestLookup.failed], never "no pull request". */
    fun lookup(directory: Path, branch: String): PullRequestLookup

    /** Slow (claude runs); never throws. */
    fun draft(worktree: Path, base: String, task: String): ChangeDescription = ChangeDescription("", "")

    /** Whether the worktree differs from [base] (tracked or untracked); unknown counts as yes. */
    fun hasChanges(worktree: Path, base: String): Boolean = true

    /** @return the URL of the new pull request */
    fun create(directory: Path, draft: PullRequestDraft): String = throw UnsupportedOperationException("No GitHub CLI")

    companion object {
        val ABSENT = object : GitHubPort {
            override val pollInterval: Duration = Duration.ofSeconds(20)
            override fun availability(directory: Path) = GhAvailability.MISSING
            override fun lookup(directory: Path, branch: String) = PullRequestLookup.none()
        }

        fun of(cli: GhCli, pullRequests: PullRequestService, drafts: ChangeDescriptionService, properties: GhProperties) = object : GitHubPort {
            override val pollInterval: Duration = properties.ciPollInterval()
            override fun availability(directory: Path) = cli.availability(directory)
            override fun lookup(directory: Path, branch: String) = pullRequests.find(directory, branch)
            override fun draft(worktree: Path, base: String, task: String) = drafts.describe(worktree, base, task)
            override fun hasChanges(worktree: Path, base: String) = drafts.hasChanges(worktree, base)
            override fun create(directory: Path, draft: PullRequestDraft) = pullRequests.create(directory, draft)
        }
    }
}

/** A linked worktree has a `.git` file, the main one a directory; the main worktree's branch never gets a CI chip. */
internal fun isLinkedWorktree(repo: Path) = Files.isRegularFile(repo.resolve(".git"))

/**
 * Re-reads gh availability and, only when gh is ready, the pull request of the current branch of a linked worktree.
 * gh failures are never an error: missing or signed-out gh is a state; an undecided availability or a failed lookup (offline,
 * rate limit) keeps the last value.
 */
suspend fun GitState.refreshPullRequest() {
    refreshBaseChanges()
    val branch = status?.branch()
    val outcome = gitCall({}) {
        when {
            branch == null || !isLinkedWorktree(repo) -> null to PullRequestLookup.none()
            else -> git.github.availability(repo).let { it to if (it == GhAvailability.READY) git.github.lookup(repo, branch) else PullRequestLookup.none() }
        }
    } ?: return
    val (availability, lookup) = outcome
    if (availability == GhAvailability.UNKNOWN) return
    availability?.let { ghStatus = it }
    if (!lookup.failed()) pullRequest = lookup.pullRequest().orElse(null)
}

/** Only a task worktree knows its base without a worktree listing; any other state keeps "unknown", which gates nothing. */
private suspend fun GitState.refreshBaseChanges() {
    val base = task?.baseBranch()?.let(::safeBase) ?: return
    gitCall({}) { git.github.hasChanges(repo, base) }?.let { baseChanges = it }
}

enum class ChipTone { NEUTRAL, SUCCESS, DANGER, WARNING }

/** The CI chip as text and tone; [url] is set when the chip opens a pull request. */
data class PullRequestChip(val text: String, val hint: String, val tone: ChipTone, val url: String? = null)

private const val INSTALL_HINT = "Install GitHub CLI: https://cli.github.com"
private const val SIGN_IN_HINT = "Run `gh auth login` in a terminal"

fun pullRequestChip(availability: GhAvailability?, pullRequest: PullRequest?, githubRemote: Boolean): PullRequestChip? = when {
    !githubRemote -> null
    availability == GhAvailability.MISSING -> PullRequestChip("GitHub CLI not installed", INSTALL_HINT, ChipTone.NEUTRAL)
    availability == GhAvailability.NOT_AUTHENTICATED -> PullRequestChip("GitHub CLI not signed in", SIGN_IN_HINT, ChipTone.NEUTRAL)
    pullRequest == null -> null
    else -> PullRequestChip("PR #${pullRequest.number()} ${checkLabel(pullRequest)}", "Open pull request #${pullRequest.number()} on GitHub", checkTone(pullRequest), pullRequest.url())
}

private fun checkLabel(pr: PullRequest) = when {
    pr.state() == PullRequestState.MERGED -> "merged"
    pr.state() == PullRequestState.CLOSED -> "closed"
    else -> when (pr.checks()) {
        CheckSummary.PASSING -> "checks passing"
        CheckSummary.FAILING -> "checks failing"
        CheckSummary.PENDING -> "checks running"
        CheckSummary.NONE -> "no checks"
    }
}

private fun checkTone(pr: PullRequest) = when {
    pr.state() != PullRequestState.OPEN -> ChipTone.NEUTRAL
    else -> when (pr.checks()) {
        CheckSummary.PASSING -> ChipTone.SUCCESS
        CheckSummary.FAILING -> ChipTone.DANGER
        CheckSummary.PENDING -> ChipTone.WARNING
        CheckSummary.NONE -> ChipTone.NEUTRAL
    }
}
