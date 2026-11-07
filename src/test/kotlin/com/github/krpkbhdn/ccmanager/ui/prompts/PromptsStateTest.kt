package com.github.krpkbhdn.ccmanager.ui.prompts

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService
import com.github.krpkbhdn.ccmanager.ui.await
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`

class PromptsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(PromptTemplateService::class.java)
    private val state = PromptsState(service, scope, { }, { })

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `a second save while the first is still running creates no second template`() {
        val release = CountDownLatch(1)
        `when`(service.create(anyString(), anyString(), anyString(), any(), any())).thenAnswer { release.await(); null }
        state.openDialog()
        val form = state.dialog!!

        state.save(form) { }
        state.save(form) { }
        release.countDown()
        await { state.dialog == null }

        verify(service, times(1)).create(anyString(), anyString(), anyString(), any(), any())
        assertThat(state.dialog).isNull()
    }
}
