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
import com.github.krpkbhdn.ccmanager.gitlab.GlabCli
import com.github.krpkbhdn.ccmanager.gitlab.MergeRequestService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** What the Git tab needs from the forge CLI (gh or glab); calls block, so callers stay off the UI thread. */
interface PullRequestPort {
    val pollInterval: Duration

    fun availability(directory: Path, remote: ForgeRemote): GhAvailability

    /** A failed CLI call is [PullRequestLookup.failed], never "no pull request". */
    fun lookup(directory: Path, remote: ForgeRemote, branch: String): PullRequestLookup

    /** Slow (claude runs); never throws. */
    fun draft(worktree: Path, base: String, task: String): ChangeDescription = ChangeDescription("", "")

    /** Whether the worktree differs from [base] (tracked or untracked); unknown counts as yes. */
    fun hasChanges(worktree: Path, base: String): Boolean = true

    /** @return the URL of the new pull or merge request */
    fun create(directory: Path, forge: Forge, draft: PullRequestDraft): String = throw UnsupportedOperationException("No forge CLI")

    companion object {
        val ABSENT = object : PullRequestPort {
            override val pollInterval: Duration = Duration.ofSeconds(20)
            override fun availability(directory: Path, remote: ForgeRemote) = GhAvailability.MISSING
            override fun lookup(directory: Path, remote: ForgeRemote, branch: String) = PullRequestLookup.none()
        }

        fun of(
            gh: GhCli,
            pullRequests: PullRequestService,
            glab: GlabCli,
            mergeRequests: MergeRequestService,
            drafts: ChangeDescriptionService,
            properties: GhProperties,
        ) = object : PullRequestPort {
            override val pollInterval: Duration = properties.ciPollInterval()
            override fun availability(directory: Path, remote: ForgeRemote) = when (remote.forge) {
                Forge.GITHUB -> gh.availability(directory)
                Forge.GITLAB -> glab.availability(directory, remote.host)
            }
            override fun lookup(directory: Path, remote: ForgeRemote, branch: String) = when (remote.forge) {
                Forge.GITHUB -> pullRequests.find(directory, branch)
                Forge.GITLAB -> mergeRequests.find(directory, remote.repository, branch)
            }
            override fun draft(worktree: Path, base: String, task: String) = drafts.describe(worktree, base, task)
            override fun hasChanges(worktree: Path, base: String) = drafts.hasChanges(worktree, base)
            override fun create(directory: Path, forge: Forge, draft: PullRequestDraft) = when (forge) {
                Forge.GITHUB -> pullRequests.create(directory, draft)
                Forge.GITLAB -> mergeRequests.create(directory, draft)
            }
        }
    }
}

/** A linked worktree has a `.git` file, the main one a directory; the main worktree's branch never gets a CI chip. */
internal fun isLinkedWorktree(repo: Path) = Files.isRegularFile(repo.resolve(".git"))

/**
 * Re-reads the CLI availability and, only when it is ready, the pull or merge request of the current branch of a linked worktree.
 * CLI failures are never an error: a missing or signed-out CLI is a state; an undecided availability or a failed lookup (offline,
 * rate limit) keeps the last value.
 */
suspend fun GitState.refreshPullRequest() {
    refreshBaseChanges()
    val branch = status?.branch()
    val remote = forgeRemote
    val outcome = gitCall({}) {
        when {
            branch == null || remote == null || !isLinkedWorktree(repo) -> null to PullRequestLookup.none()
            else -> git.forge.availability(repo, remote).let { it to if (it == GhAvailability.READY) git.forge.lookup(repo, remote, branch) else PullRequestLookup.none() }
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
    gitCall({}) { git.forge.hasChanges(repo, base) }?.let { baseChanges = it }
}

enum class ChipTone { NEUTRAL, SUCCESS, DANGER, WARNING }

/** The CI chip as text and tone; [url] is set when the chip opens a pull request. */
data class PullRequestChip(val text: String, val hint: String, val tone: ChipTone, val url: String? = null)

fun pullRequestChip(availability: GhAvailability?, pullRequest: PullRequest?, remote: ForgeRemote?): PullRequestChip? = when {
    remote == null -> null
    availability == GhAvailability.MISSING -> PullRequestChip("${remote.forge.cliName} not installed", remote.forge.installHint, ChipTone.NEUTRAL)
    availability == GhAvailability.NOT_AUTHENTICATED -> PullRequestChip("${remote.forge.cliName} not signed in", remote.signInHint(), ChipTone.NEUTRAL)
    pullRequest == null -> null
    else -> {
        val id = "${remote.forge.idPrefix}${pullRequest.number()}"
        PullRequestChip("${remote.forge.shortName} $id ${checkLabel(pullRequest)}", "Open ${remote.forge.longName} $id on ${remote.host}", checkTone(pullRequest), pullRequest.url())
    }
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
