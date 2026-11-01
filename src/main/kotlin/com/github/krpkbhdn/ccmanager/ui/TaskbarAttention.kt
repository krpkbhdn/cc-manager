package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
import com.github.krpkbhdn.ccmanager.ui.state.isEnabled
import java.awt.Taskbar
import java.awt.Window
import javax.swing.SwingUtilities
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(TaskbarAttention::class.java)
private const val BADGE_PIXELS = 32

/** The taskbar button flashes only for a window that exists on screen (even minimized) but is not the one in use. */
fun shouldFlash(settings: UiSettings, kind: NotificationKind, windowActive: Boolean, windowVisible: Boolean): Boolean =
    !settings.doNotDisturb && isEnabled(settings, kind) && !windowActive && windowVisible

/** Text for the dock badge, or null when no session waits. */
fun taskbarBadge(waiting: Int): String? = if (waiting > 0) badgeText(waiting) else null

/** Flashes the taskbar button and shows the waiting count on it where the OS allows; silently does nothing elsewhere. */
class TaskbarAttention {
    @Volatile
    var window: Window? = null

    fun flashFor(settings: UiSettings, kind: NotificationKind, windowActive: Boolean) {
        val target = window ?: return
        if (!shouldFlash(settings, kind, windowActive, target.isVisible)) return
        onTaskbar { if (it.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) it.requestWindowUserAttention(target) }
    }

    fun showWaiting(waiting: Int) {
        val target = window
        val text = taskbarBadge(waiting)
        onTaskbar {
            if (it.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER)) it.setIconBadge(text)
            if (target != null && it.isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW)) {
                it.setWindowIconBadge(target, if (waiting > 0) drawBadgeOverlay(BADGE_PIXELS, waiting) else null)
            }
        }
    }

    private fun onTaskbar(action: (Taskbar) -> Unit) {
        if (!Taskbar.isTaskbarSupported()) return
        SwingUtilities.invokeLater {
            try {
                action(Taskbar.getTaskbar())
            } catch (e: UnsupportedOperationException) {
                log.debug("Taskbar feature unavailable", e)
            } catch (e: SecurityException) {
                log.debug("Taskbar access denied", e)
            }
        }
    }
}
