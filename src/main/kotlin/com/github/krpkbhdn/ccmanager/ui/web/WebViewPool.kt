package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.runtime.mutableStateMapOf
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import com.github.krpkbhdn.ccmanager.web.BrowserLimits
import java.time.Clock
import kotlinx.coroutines.Job

/**
 * The live browsers, one per project. At most `maxLiveBrowsers` stay open (least recently shown goes first) and a browser hidden
 * longer than `idleBrowserTtl` is closed. A browser that is shown or holds an unsaved draft is never closed.
 */
internal class WebViewPool(
    private val hostFactory: () -> WebPageHost,
    private val limits: () -> BrowserLimits,
    private val clock: Clock,
    private val wire: (WebView) -> Unit,
) {
    private val views = mutableStateMapOf<Long, WebView>()
    private val shownProjects = mutableSetOf<Long>()

    fun find(projectId: Long): WebView? = views[projectId]

    @Synchronized
    fun open(projectId: Long): WebView {
        views[projectId]?.let { return it }
        val view = WebView(projectId, hostFactory())
        view.shown = projectId in shownProjects
        view.lastShownMillis = clock.millis()
        views[projectId] = view
        wire(view)
        trimToLimit(view)
        return view
    }

    @Synchronized
    fun shown(projectId: Long) {
        shownProjects += projectId
        views[projectId]?.let { it.shown = true; it.lastShownMillis = clock.millis() }
    }

    @Synchronized
    fun hidden(projectId: Long) {
        shownProjects -= projectId
        views[projectId]?.let { it.shown = false; it.lastShownMillis = clock.millis() }
    }

    @Synchronized
    fun closeIdle() {
        val ttl = limits().idleBrowserTtl().toMillis()
        val now = clock.millis()
        views.values.filter { evictable(it) && now - it.lastShownMillis > ttl }.forEach { close(it.projectId) }
    }

    @Synchronized
    fun close(projectId: Long) {
        views.remove(projectId)?.let { view ->
            view.jobs.forEach(Job::cancel)
            view.host.close()
        }
    }

    @Synchronized
    fun retain(existing: Collection<Long>) = views.keys.filter { it !in existing }.forEach(::close)

    @Synchronized
    fun closeAll() = views.keys.toList().forEach(::close)

    private fun trimToLimit(keep: WebView) {
        while (views.size > limits().maxLiveBrowsers()) {
            val oldest = views.values.filter { it !== keep && evictable(it) }.minByOrNull { it.lastShownMillis } ?: return
            close(oldest.projectId)
        }
    }

    private fun evictable(view: WebView) = !view.shown && !view.draftDirty
}
