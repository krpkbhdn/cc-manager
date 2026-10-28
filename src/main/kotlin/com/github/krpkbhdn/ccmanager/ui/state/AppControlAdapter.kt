package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.app.AppControl
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
        state.terminal.open(session)
        state.views[session.projectId] = state.viewOf(session.projectId).openSession(session.ccmSessionId())
    }

    override fun queuePrompt(ccmSessionId: String, text: String) = onUi { state.queue.add(ccmSessionId, text) }

    private fun onUi(action: () -> Unit) {
        val done = CompletableFuture<Unit>()
        scope.launch { runCatching(action).fold({ done.complete(Unit) }, done::completeExceptionally) }
        try {
            done.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw IllegalStateException("The UI did not respond within $timeout", e)
        } catch (e: ExecutionException) {
            throw (e.cause as? RuntimeException) ?: IllegalStateException(e.cause)
        }
    }

    private companion object {
        val ROUTES = mapOf(
            "overview" to Route.Overview,
            "sessions" to Route.Sessions,
            "assistant" to Route.Assistant,
            "teams" to Route.Teams,
            "prompts" to Route.Prompts,
        )
    }
}
