package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.CcManagerApplication
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.ProjectService
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

private const val WAIT_MILLIS = 90_000L
private const val SETTLE_MILLIS = 3_000L
private val userSettings: Path = Path.of(System.getProperty("user.home"), ".claude", "settings.json")

/**
 * Real claude, one tiny prompt: the project's selection reaches a launched session as flags, and the same writer the UI uses
 * (text, pause, Enter) switches model and effort with /model and /effort, which the statusLine reports. Claude saves those
 * as the user's defaults, so the test restores ~/.claude/settings.json byte for byte.
 */
class RealSlashCommandTest {
    @TempDir
    lateinit var dataDir: Path

    private class PtyPort(val sessions: MutableMap<String, LaunchedSession>) : TerminalPort {
        override fun open(session: LaunchedSession) { sessions[session.ccmSessionId()] = session }
        override fun send(ccmSessionId: String, text: String) {
            sessions.getValue(ccmSessionId).process().outputStream.apply { write(text.toByteArray()); flush() }
        }
        override fun close(ccmSessionId: String) = Unit

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }

    @Test
    fun launchFlagsThenSlashCommandsReachTheRunningSession() {
        assumeTrue(System.getenv("CCM_REAL_CLAUDE") == "1")
        val settingsBefore = if (Files.exists(userSettings)) Files.readAllBytes(userSettings) else null
        try {
            CcManagerApplication.start(arrayOf("--cc-manager.data-dir=$dataDir")).use { context ->
                val projects = context.getBean(ProjectService::class.java)
                val project = projects.add(Path.of("").toAbsolutePath())
                projects.selectModel(project.id(), ModelSelection("haiku", "low"))
                val registry = context.getBean(SessionRegistry::class.java)
                val usage = context.getBean(SessionUsageService::class.java)
                val session = context.getBean(ClaudeLauncher::class.java).launch(project.id(), LaunchMode.NEW)
                drain(session)
                val port = PtyPort(mutableMapOf(session.ccmSessionId() to session))
                val writer = SessionCommandWriter(port)
                val id = session.ccmSessionId()
                await("session registered") { registry.find(id).map { it.claudeSessionId() != null }.orElse(false) }
                Thread.sleep(SETTLE_MILLIS)
                runBlocking { writer.run(id, "say hi in two words") }
                await("first statusLine") { usage.of(id).isPresent }
                println("LAUNCHED_WITH ${usage.of(id).get().modelName()} ${usage.of(id).get().effort()}")
                assertThat(usage.of(id).get().modelName()).containsIgnoringCase("haiku")
                assertThat(usage.of(id).get().effort()).isEqualTo("low")

                runBlocking { writer.run(id, modelCommand("sonnet")) }
                await("model switched") { modelReported(usage.of(id).get().modelName(), "sonnet") }
                runBlocking { writer.run(id, effortCommand("high")) }
                await("effort switched") { usage.of(id).get().effort() == "high" }
                println("AFTER_COMMANDS ${usage.of(id).get().modelName()} ${usage.of(id).get().effort()}")
                runBlocking { writer.run(id, "/exit") }
                await("exit") { registry.find(id).isEmpty }
            }
        } finally {
            if (settingsBefore != null) Files.write(userSettings, settingsBefore)
        }
        if (settingsBefore != null) assertThat(Files.readAllBytes(userSettings)).isEqualTo(settingsBefore)
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertThat(condition()).describedAs("$what within ${WAIT_MILLIS}ms").isTrue()
    }
}

/** Nobody reads the PTY in a test, and an unread console output buffer eventually stalls claude's rendering. */
private fun drain(session: LaunchedSession) {
    val input = session.process().inputStream
    Thread { runCatching { val buffer = ByteArray(8192); while (input.read(buffer) >= 0) Unit } }.apply { isDaemon = true }.start()
}
