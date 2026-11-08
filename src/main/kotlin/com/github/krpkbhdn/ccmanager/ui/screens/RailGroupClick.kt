package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Toolkit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_DOUBLE_CLICK_MILLIS = 400L

/** True when a click at [now] follows one at [previous] within [intervalMillis]. */
internal fun isDoubleClick(previous: Long?, now: Long, intervalMillis: Long): Boolean = previous != null && now - previous <= intervalMillis

private fun systemDoubleClickMillis(): Long =
    (Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int)?.toLong() ?: DEFAULT_DOUBLE_CLICK_MILLIS

/** Rail group click: a single click acts after the double-click interval, a double click runs [onDouble] instead and never [onSingle]. */
@Composable
fun rememberRailGroupClick(onSingle: () -> Unit, onDouble: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onSingle to onDouble)
    return remember {
        var lastClick: Long? = null
        var pending: Job? = null
        val handler: () -> Unit = {
            val now = System.currentTimeMillis()
            val interval = systemDoubleClickMillis()
            pending?.cancel()
            if (isDoubleClick(lastClick, now, interval)) {
                lastClick = null
                latest.value.second()
            } else {
                lastClick = now
                pending = scope.launch {
                    delay(interval)
                    lastClick = null
                    latest.value.first()
                }
            }
        }
        handler
    }
}
