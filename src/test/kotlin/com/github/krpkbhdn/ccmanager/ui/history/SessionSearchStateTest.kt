package com.github.krpkbhdn.ccmanager.ui.history

import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import com.github.krpkbhdn.ccmanager.history.SessionSearchService
import com.github.krpkbhdn.ccmanager.ui.await
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

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
    fun `a failing entry counter is reported once, not on every refresh`() {
        `when`(service.indexedEntryCount()).thenThrow(IllegalStateException("index broken"))
        state = SessionSearchState(service, mock(TranscriptService::class.java), scope, { }, { errors += it }, refreshMillis = 20)

        state.start()
        await { errors.isNotEmpty() }
        Thread.sleep(200)

        assertThat(errors).containsExactly("index broken")
    }
}
