package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitBranchService
import com.github.krpkbhdn.ccmanager.git.GitConflictService
import com.github.krpkbhdn.ccmanager.git.GitHistoryService
import com.github.krpkbhdn.ccmanager.git.GitIndexService
import com.github.krpkbhdn.ccmanager.git.GitLogService
import com.github.krpkbhdn.ccmanager.git.GitStashService
import com.github.krpkbhdn.ccmanager.git.GitStatusService
import com.github.krpkbhdn.ccmanager.git.GitSyncService
import com.github.krpkbhdn.ccmanager.git.GitWatcher
import com.github.krpkbhdn.ccmanager.git.TaskWorktreeService
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService
import com.github.krpkbhdn.ccmanager.github.GhCli
import com.github.krpkbhdn.ccmanager.github.GhProperties
import com.github.krpkbhdn.ccmanager.git.ChangeDescriptionService
import com.github.krpkbhdn.ccmanager.github.PullRequestService
import com.github.krpkbhdn.ccmanager.gitlab.GlabCli
import com.github.krpkbhdn.ccmanager.gitlab.MergeRequestService
import org.springframework.context.ConfigurableApplicationContext

/** Every git service the Git tab talks to; all calls block, so callers stay off the UI thread. */
class GitBackend(
    val status: GitStatusService,
    val index: GitIndexService,
    val branches: GitBranchService,
    val sync: GitSyncService,
    val log: GitLogService,
    val stashes: GitStashService,
    val history: GitHistoryService,
    val conflicts: GitConflictService,
    val worktrees: GitWorktreeService,
    val watcher: GitWatcher,
    val taskWorktrees: TaskWorktreeService,
    val forge: PullRequestPort = PullRequestPort.ABSENT,
) {
    companion object {
        fun from(context: ConfigurableApplicationContext) = GitBackend(
            status = context.getBean(GitStatusService::class.java),
            index = context.getBean(GitIndexService::class.java),
            branches = context.getBean(GitBranchService::class.java),
            sync = context.getBean(GitSyncService::class.java),
            log = context.getBean(GitLogService::class.java),
            stashes = context.getBean(GitStashService::class.java),
            history = context.getBean(GitHistoryService::class.java),
            conflicts = context.getBean(GitConflictService::class.java),
            worktrees = context.getBean(GitWorktreeService::class.java),
            watcher = context.getBean(GitWatcher::class.java),
            taskWorktrees = context.getBean(TaskWorktreeService::class.java),
            forge = PullRequestPort.of(
                context.getBean(GhCli::class.java), context.getBean(PullRequestService::class.java),
                context.getBean(GlabCli::class.java), context.getBean(MergeRequestService::class.java),
                context.getBean(ChangeDescriptionService::class.java), context.getBean(GhProperties::class.java),
            ),
        )
    }
}
