package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.app.AppControl
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lets the Assistant's MCP tools (any thread) drive the UI: each call runs on [scope], which must be the UI dispatcher, and waits for it. */
class AppControlAdapter(private val state: AppState, private val scope: CoroutineScope, private val timeout: Duration) : AppControl {

    override fun openProject(projectId: Long, tab: String) = onUi {
        check(state.projects.find(projectId) != null) { "Unknown project $projectId" }
        val view = state.viewOf(projectId)
        val target = when (tab) {
            "sessions" -> view.openSessions.lastOrNull()?.let { ProjectTab.Session(it) } ?: view.selected
            "git" -> ProjectTab.Git
            "run" -> ProjectTab.Run
            else -> error("Unknown tab '$tab', expected sessions, git or run")
        }
        state.select(projectId, target)
        state.navigate(Route.Project(projectId))
    }

    override fun navigate(route: String) = onUi {
        state.navigate(ROUTES[route] ?: error("Unknown route '$route', expected ${ROUTES.keys.joinToString()}"))
    }

    override fun attachSession(session: LaunchedSession) = onUi {
        state.sessions.adopt(session)
    }

    override fun queuePrompt(ccmSessionId: String, text: String) = onUi {
        state.queue.add(ccmSessionId, text)
    }

    /** The timeout error is reported only when the action did not start; one that already started is awaited so a retry cannot repeat it. */
    internal fun onUi(action: () -> Unit) {
        val done = CompletableFuture<Unit>()
        val started = AtomicBoolean()
        val job = scope.launch {
            if (started.compareAndSet(false, true)) runCatching(action).fold({ done.complete(Unit) }, done::completeExceptionally)
        }
        try {
            try {
                done.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                if (!started.compareAndSet(false, true)) return awaitFinish(done)
                job.cancel()
                throw IllegalStateException("The UI did not respond within $timeout", e)
            }
        } catch (e: ExecutionException) {
            throw (e.cause as? RuntimeException) ?: IllegalStateException(e.cause)
        }
    }

    private fun awaitFinish(done: CompletableFuture<Unit>) {
        val limit = timeout.multipliedBy(FINISH_TIMEOUT_FACTOR)
        try {
            done.get(limit.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw IllegalStateException("The UI action started but did not finish within $limit; do not repeat it blindly", e)
        }
    }

    private companion object {
        const val FINISH_TIMEOUT_FACTOR = 4L

        val ROUTES = mapOf(
            "overview" to Route.Overview,
            "sessions" to Route.Sessions,
            "assistant" to Route.Assistant,
            "teams" to Route.Teams,
            "prompts" to Route.Prompts,
        )
    }
}
