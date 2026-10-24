package com.github.krpkbhdn.ccmanager.ui.git

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitDemoRepoTest {
    @Test
    fun `the demo repository has history branches a stash and local changes`(@TempDir sandbox: Path) {
        assumeThat(runCatching { GitDemoRepo.git(sandbox, "--version") }.isSuccess).`as`("git is installed").isTrue()

        val work = GitDemoRepo.create(sandbox)

        assertThat(GitDemoRepo.git(work, "branch", "--list")).contains("main", "feature/login", "feature/draft")
        assertThat(GitDemoRepo.git(work, "log", "--oneline", "--all")).contains("Merge branch 'feature/login'")
        assertThat(GitDemoRepo.git(work, "stash", "list")).contains("parked readme edit")
        assertThat(GitDemoRepo.git(work, "tag")).contains("v0.1.0")
        val status = GitDemoRepo.git(work, "status", "--porcelain")
        assertThat(status).contains("M  src/Login.java", " M src/App.java", "?? src/Scratch.java")
        GitDemoRepo.git(work, "fetch")
        assertThat(GitDemoRepo.git(work, "rev-list", "--count", "main..origin/main").trim()).isEqualTo("1")
    }
}
