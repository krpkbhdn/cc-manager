package com.github.krpkbhdn.ccmanager.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UNEXPECTED_ERROR = "Something went wrong"

internal suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/** Runs [block], reporting runtime failures to [onError] while letting cancellation through. */
internal suspend fun runGuarded(onError: (String) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        onError(e.message ?: UNEXPECTED_ERROR)
    }
}
