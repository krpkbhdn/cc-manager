package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.workspace.ProcessTree
import java.util.concurrent.TimeUnit

private const val CTRL_C = "\u0003"
private const val EXIT_KEY_PRESSES = 3
internal const val EXIT_KEY_GAP_MILLIS = 150L
internal const val EXIT_GRACE_MILLIS = 3_000L

/**
 * Ends claude the way a user would (Ctrl+C clears input or interrupts a turn, repeated it exits), because claude counts
 * a start that is force-killed as a failed fullscreen start and disables fullscreen globally after two of them.
 */
internal fun requestExit(session: TerminalSession) {
    repeat(EXIT_KEY_PRESSES) {
        session.send(CTRL_C)
        Thread.sleep(EXIT_KEY_GAP_MILLIS)
    }
}

/** Waits at most [graceMillis] in total for the processes to exit on their own, then kills the process trees of the rest. */
internal fun killUnlessExited(processes: Collection<Process>, graceMillis: Long) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(graceMillis)
    processes.forEach { process ->
        val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
        if (!process.waitFor(remaining, TimeUnit.NANOSECONDS)) ProcessTree.kill(process)
    }
}
