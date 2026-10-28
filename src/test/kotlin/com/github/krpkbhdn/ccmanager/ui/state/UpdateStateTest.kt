package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.update.UpdateService
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import com.github.krpkbhdn.ccmanager.ui.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UpdateStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(UpdateService::class.java).also {
        `when`(it.enabled()).thenReturn(true)
        `when`(it.checkForUpdate()).thenReturn(Optional.empty())
    }
    private val update = UpdateState(service, scope, { _, _ -> }, { it() }) {}

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `a manual check that finds nothing reports up to date`() {
        update.checkNow()

        await { !update.checking && update.upToDate }
        assertThat(update.upToDate).isTrue()
        assertThat(update.status).isEqualTo(UpdateStatus.None)
    }

    @Test
    fun `a manual check that fails reports the failure instead of up to date`() {
        `when`(service.checkForUpdate()).thenThrow(IllegalStateException("offline"))

        update.checkNow()

        await { !update.checking && update.checkFailed }
        assertThat(update.upToDate).isFalse()
    }

    @Test
    fun `a retry after a failure clears it`() {
        `when`(service.checkForUpdate()).thenThrow(IllegalStateException("offline")).thenReturn(Optional.empty())

        update.checkNow()
        await { !update.checking && update.checkFailed }
        update.checkNow()

        await { !update.checking && update.upToDate }
        assertThat(update.checkFailed).isFalse()
    }

    @Test
    fun `a manual check does nothing when updates are disabled`() {
        `when`(service.enabled()).thenReturn(false)

        update.checkNow()

        assertThat(update.checking).isFalse()
        assertThat(update.upToDate).isFalse()
    }
}
