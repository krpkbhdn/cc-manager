package com.github.krpkbhdn.ccmanager.ui.run

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RunModelsTest {
    private val project = Path.of("work", "app").toAbsolutePath()

    @Test
    fun `the project folder itself is called project root`() {
        assertThat(directoryHint(project, project)).isEqualTo("project root")
    }

    @Test
    fun `a worktree inside the project is shown relative to it`() {
        assertThat(directoryHint(project, project.resolve(".claude/worktrees/feature-x"))).isEqualTo(".claude/worktrees/feature-x")
    }

    @Test
    fun `a folder outside the project stays absolute`() {
        val outside = project.resolveSibling("other").toAbsolutePath()
        assertThat(directoryHint(project, outside)).isEqualTo(outside.toString())
    }
}
