package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StrandedWorktreeMessageTest {
    @Test
    fun `names the reason, the folder and the branch that stay behind`() {
        val message = strandedWorktreeMessage(TaskWorktree(1, 1, Path.of("repo-task-x"), "task/x", "main", "x"), "The session did not start")
        assertThat(message).contains("The session did not start", "repo-task-x", "task/x", "still there")
    }
}
