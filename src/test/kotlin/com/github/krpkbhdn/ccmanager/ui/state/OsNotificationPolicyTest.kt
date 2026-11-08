package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OsNotificationPolicyTest {
    private val defaults = UiSettings()

    private fun alert(project: String, sessionId: String = "s-$project", kind: NotificationKind = NotificationKind.NEEDS_ACTION) =
        OsAlert(kind, project.length.toLong(), project, sessionId, "Question needs your answer", "Which one?")

    @Test
    fun `desktop popup shows only while the window is in the background`() {
        assertThat(shouldNotify(defaults, NotificationKind.NEEDS_ACTION, windowActive = false)).isTrue()
        assertThat(shouldNotify(defaults, NotificationKind.NEEDS_ACTION, windowActive = true)).isFalse()
    }

    @Test
    fun `do not disturb and disabled kinds show nothing`() {
        assertThat(shouldNotify(defaults.copy(doNotDisturb = true), NotificationKind.ERROR, false)).isFalse()
        assertThat(shouldNotify(defaults.copy(osNotifyFinished = false), NotificationKind.FINISHED, false)).isFalse()
        assertThat(shouldNotify(defaults, NotificationKind.FINISHED, false)).isTrue()
        assertThat(shouldNotify(defaults, NotificationKind.ERROR, false)).isTrue()
        assertThat(shouldNotify(defaults, NotificationKind.INFO, false)).isFalse()
        assertThat(shouldNotify(defaults, NotificationKind.USAGE_ALERT, false)).isTrue()
    }

    @Test
    fun `a single alert names project and target session`() {
        val notice = summarize(listOf(alert("api")))
        assertThat(notice.title).isEqualTo("api: Question needs your answer")
        assertThat(notice.body).isEqualTo("Which one?")
        assertThat(notice.ccmSessionId).isEqualTo("s-api")
        assertThat(notice.projectId).isEqualTo(3L)
    }

    @Test
    fun `several waiting alerts become one summary without a target`() {
        val notice = summarize(listOf(alert("api"), alert("web"), alert("api", "s-2")))
        assertThat(notice.title).isEqualTo("3 sessions need your action")
        assertThat(notice.body).isEqualTo("api, web")
        assertThat(notice.ccmSessionId).isNull()
        assertThat(notice.projectId).isNull()
        assertThat(notice.kind).isEqualTo(NotificationKind.NEEDS_ACTION)
        assertThat(notice.summary).isTrue()
    }

    @Test
    fun `mixed alerts say attention and long project lists are cut`() {
        val notice = summarize(listOf(alert("a"), alert("b"), alert("c"), alert("d"), alert("e", kind = NotificationKind.ERROR)))
        assertThat(notice.title).isEqualTo("5 sessions need attention")
        assertThat(notice.body).isEqualTo("a, b, c and 2 more")
        assertThat(summarize(listOf(alert("a", kind = NotificationKind.FINISHED), alert("b", kind = NotificationKind.ERROR))).kind).isEqualTo(NotificationKind.FINISHED)
    }
}
