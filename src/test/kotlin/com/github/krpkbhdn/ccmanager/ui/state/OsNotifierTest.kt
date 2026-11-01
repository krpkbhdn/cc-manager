package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class OsNotifierTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val shown = java.util.concurrent.CopyOnWriteArrayList<OsNotice>()
    private var settings = UiSettings()
    private var windowActive = false
    private var waiting = true
    private var failShow = false

    private fun notifier() = OsNotifier(
        scope, { settings }, { windowActive }, { waiting },
        { OsAlert(it.kind, it.projectId, "p${it.projectId}", it.ccmSessionId, it.title, it.body) },
        { if (failShow) error("tray gone"); shown += it },
    )

    private fun note(kind: NotificationKind = NotificationKind.NEEDS_ACTION, project: Long = 1) =
        AppNotification(1, Instant.EPOCH, kind, project, "s$project", "Question needs your answer", "b")

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `alerts inside the window become one notice and a later one starts a new window`() = runBlocking<Unit> {
        val n = notifier()
        n.offer(note(project = 1)); delay(300); n.offer(note(project = 2))
        delay(1000)
        assertThat(shown).isEmpty()
        delay(800)
        assertThat(shown).hasSize(1)
        assertThat(shown[0].title).isEqualTo("2 sessions need your action")
        n.offer(note(project = 3))
        delay(1800)
        assertThat(shown).hasSize(2)
        assertThat(shown[1].ccmSessionId).isEqualTo("s3")
    }

    @Test
    fun `needs action and finished in one group say attention`() = runBlocking<Unit> {
        settings = settings.copy(osNotifyFinished = true)
        val n = notifier()
        n.offer(note()); n.offer(note(NotificationKind.FINISHED, 2))
        delay(1800)
        assertThat(shown).hasSize(1)
        assertThat(shown[0].title).isEqualTo("2 sessions need attention")
    }

    @Test
    fun `active window, do not disturb and a disabled type show nothing`() = runBlocking<Unit> {
        val n = notifier()
        windowActive = true
        n.offer(note())
        windowActive = false; settings = settings.copy(doNotDisturb = true)
        n.offer(note())
        settings = UiSettings(osNotifyFinished = false)
        n.offer(note(NotificationKind.FINISHED))
        delay(1800)
        assertThat(shown).isEmpty()
    }

    @Test
    fun `an alert whose session stopped waiting or whose window became active is dropped at flush`() = runBlocking<Unit> {
        val n = notifier()
        n.offer(note()); waiting = false
        delay(1800)
        assertThat(shown).isEmpty()
        waiting = true
        n.offer(note()); windowActive = true
        delay(1800)
        assertThat(shown).isEmpty()
    }

    @Test
    fun `a failing show does not block the next batch`() = runBlocking<Unit> {
        val n = notifier()
        failShow = true
        n.offer(note()); delay(1800)
        failShow = false
        n.offer(note(project = 2)); delay(1800)
        assertThat(shown).hasSize(1)
        assertThat(shown[0].ccmSessionId).isEqualTo("s2")
    }
}
