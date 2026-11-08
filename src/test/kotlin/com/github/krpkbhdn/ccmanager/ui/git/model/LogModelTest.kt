package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.git.GitLogService
import com.github.krpkbhdn.ccmanager.git.GraphState
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class LogModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val data = SampleRepos.busy()
    private val errors = mutableListOf<GitException>()
    @Volatile private var failing = false
    @Volatile private var failingDetails = false
    @Volatile private var moreAfterFirstPage = false

    @AfterEach
    fun stop() = scope.cancel()

    private fun model(): LogModel {
        val fake = FakeGit(data).backend
        val log = object : GitLogService(com.github.krpkbhdn.ccmanager.git.GitRunner()) {
            override fun log(repo: Path, query: GitLog.Query, previous: GraphState?): StatefulPage {
                if (failing) throw GitException(GitException.Kind.OTHER, "log failed", "")
                return StatefulPage(GitLog.Page(data.rows.take(3), moreAfterFirstPage), GraphState(emptyList(), 3))
            }

            override fun details(repo: Path, hash: String): GitLog.Details {
                if (failingDetails) throw GitException(GitException.Kind.OTHER, "details failed", "")
                return GitLog.Details(data.rows.first().commit(), emptyList())
            }
        }
        val backend = GitBackend(fake.status, fake.index, fake.branches, fake.sync, log, fake.stashes, fake.history, fake.conflicts, fake.worktrees, fake.watcher, fake.taskWorktrees)
        return LogModel({ Path.of("repo") }, backend, scope) { errors += it }
    }

    @Test
    fun `a failed reload empties the list and lets the view load again`() {
        val log = model()
        log.reload()
        await { log.rows.isNotEmpty() && !log.loading }
        moreAfterFirstPage = true
        failing = true

        log.update { it.copy(author = "Priya") }
        await { !log.loading }

        assertThat(log.rows).isEmpty()
        assertThat(log.hasMore).isFalse()
        assertThat(log.loaded).isFalse()
    }

    @Test
    fun `a failing details call drops the selection instead of spinning`() {
        val log = model()
        failingDetails = true

        log.select("abc")
        await { errors.isNotEmpty() }

        await { log.selected == null }
        assertThat(log.details).isNull()
    }

    @Test
    fun `a selected commit beyond the first page survives a reload`() {
        val log = model()
        moreAfterFirstPage = true
        log.select("beyond-first-page")
        await { log.details != null }

        log.reload()
        await { log.rows.isNotEmpty() && !log.loading }

        assertThat(log.selected).isEqualTo("beyond-first-page")
    }
}
