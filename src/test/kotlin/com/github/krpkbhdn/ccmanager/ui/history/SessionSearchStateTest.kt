package com.github.krpkbhdn.ccmanager.ui.history

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import com.github.krpkbhdn.ccmanager.history.SessionSearchService
import com.github.krpkbhdn.ccmanager.ui.await
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private const val POLLS_AFTER_FAILURE = 3

class SessionSearchStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(SessionSearchService::class.java)
    private val errors = CopyOnWriteArrayList<String>()
    private lateinit var state: SessionSearchState

    @AfterEach
    fun stop() {
        state.dispose()
        scope.cancel()
    }

    @Test
    fun `watching the index shows the current entry count`() {
        `when`(service.indexedEntryCount()).thenReturn(7L)
        state = SessionSearchState(service, mock(TranscriptService::class.java), scope, { }, { errors += it })

        val watcher = scope.launch { state.watchIndexedEntries() }
        await { state.indexedEntries == 7L }
        watcher.cancel()
    }

    @Test
    fun `a failing entry counter is reported once, not on every refresh`() {
        `when`(service.indexedEntryCount()).thenThrow(IllegalStateException("index broken"))
        state = SessionSearchState(service, mock(TranscriptService::class.java), scope, { }, { errors += it })

        state.start()
        await { errors.isNotEmpty() }
        state.query.setTextAndPlaceCursorAtEnd("a")
        Snapshot.sendApplyNotifications()
        verify(service, timeout(5_000).atLeast(2)).indexedEntryCount()
        state.query.setTextAndPlaceCursorAtEnd("ab")
        Snapshot.sendApplyNotifications()
        verify(service, timeout(5_000).atLeast(POLLS_AFTER_FAILURE - 1)).indexedEntryCount()

        assertThat(errors).containsExactly("index broken")
    }
}
