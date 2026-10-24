package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitException.Kind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitErrorPlanTest {
    private fun plan(kind: Kind, message: String = "boom", output: String = "boom") = errorPlan(GitException(kind, message, output), "Pushing")

    @Test
    fun `auth failures explain that git cannot prompt and offer the fetch command`() {
        val plan = plan(Kind.AUTH)

        assertThat(plan.hint).contains("cannot ask for a password")
        assertThat(plan.actions).containsExactly(ErrorAction.COPY_FETCH_COMMAND, ErrorAction.RETRY)
    }

    @Test
    fun `a rejected push offers pull and force push`() {
        assertThat(plan(Kind.NON_FAST_FORWARD).actions).containsExactly(ErrorAction.PULL, ErrorAction.FORCE_PUSH)
    }

    @Test
    fun `conflicts jump to the conflicts list`() {
        assertThat(plan(Kind.CONFLICT).actions).containsExactly(ErrorAction.SHOW_CONFLICTS)
    }

    @Test
    fun `a locked repository can be retried`() {
        assertThat(plan(Kind.LOCKED).actions).containsExactly(ErrorAction.RETRY)
    }

    @Test
    fun `missing git is explained with its download page`() {
        val plan = plan(Kind.GIT_MISSING)

        assertThat(plan.hint).contains("git-scm.com")
        assertThat(plan.actions).isEmpty()
    }

    @Test
    fun `a dirty tree offers stash and retry`() {
        assertThat(plan(Kind.DIRTY_TREE).actions).containsExactly(ErrorAction.STASH_AND_RETRY)
        assertThat(plan(Kind.NO_UPSTREAM).actions).containsExactly(ErrorAction.SET_UPSTREAM)
    }

    @Test
    fun `long output adds a details action`() {
        val plan = plan(Kind.OTHER, message = "failed", output = "failed\nline two\nline three")

        assertThat(plan.actions).containsExactly(ErrorAction.SHOW_DETAILS)
        assertThat(plan.details).contains("line three")
        assertThat(plan.headline).isEqualTo("failed")
    }

    @Test
    fun `short single line output needs no details`() {
        assertThat(plan(Kind.OTHER, message = "boom", output = "boom").actions).isEmpty()
        assertThat(plan(Kind.OTHER, message = "boom", output = "").details).isEqualTo("boom")
    }
}
