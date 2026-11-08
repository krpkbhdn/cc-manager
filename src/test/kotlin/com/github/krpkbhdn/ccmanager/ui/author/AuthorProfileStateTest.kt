package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorArea
import com.github.krpkbhdn.ccmanager.author.AuthorCatalog
import com.github.krpkbhdn.ccmanager.author.AuthorCatalogService
import com.github.krpkbhdn.ccmanager.author.AuthorInstallReport
import com.github.krpkbhdn.ccmanager.author.AuthorKind
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private const val WAIT_MILLIS = 5_000L

class AuthorProfileStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(AuthorCatalogService::class.java)
    private val toasts = CopyOnWriteArrayList<String>()
    private val errors = CopyOnWriteArrayList<String>()

    private val market = authorItem("market", group = "Marketplaces")
    private val plugin = authorItem("plugin", requires = listOf("market"))
    private val team = authorItem("team", area = AuthorArea.CLAUDE_CONFIG, group = "Teams", kind = AuthorKind.TEAM)
    private val items = AuthorCatalog(listOf(market, plugin, team), emptyList())

    private val state = AuthorProfileState(
        service, scope, mock(McpState::class.java), { errors += it },
        { _: NotificationKind, title: String, _: String -> toasts += title },
    )

    init {
        `when`(service.catalog()).thenReturn(items)
        `when`(service.entries()).thenReturn(listOf(authorEntry(market), authorEntry(plugin), authorEntry(team)))
        `when`(service.install(anyList())).thenReturn(AuthorInstallReport(emptyList()))
    }

    @AfterEach
    fun stop() {
        scope.cancel()
        assertThat(errors).isEmpty()
    }

    private fun awaitIdle() = await { scope.coroutineContext.job.children.none { it.isActive } }

    private fun openStartOffer() {
        state.offerAtStart()
        await { state.catalog != null }
    }

    @Test
    fun startPreselectsNewItemsAndLeavesAReplacingTeamUnchecked() {
        `when`(service.entries()).thenReturn(listOf(authorEntry(market), authorEntry(plugin, AuthorStatus.UPDATE_AVAILABLE), authorEntry(team, replaces = true)))

        openStartOffer()

        val view = state.catalog!!
        assertThat(view.offer).isTrue()
        assertThat(view.onlyNew).isTrue()
        assertThat(view.selected).containsExactlyInAnyOrder("market", "plugin")
    }

    @Test
    fun startPreselectsNothingInstalledOrDeclined() {
        `when`(service.entries()).thenReturn(listOf(authorEntry(market, AuthorStatus.INSTALLED), authorEntry(plugin, AuthorStatus.DECLINED), authorEntry(team)))

        openStartOffer()

        assertThat(state.catalog!!.selected).containsExactly("team")
    }

    @Test
    fun startShowsNothingWhenEverythingIsDecided() {
        `when`(service.entries()).thenReturn(listOf(authorEntry(market, AuthorStatus.INSTALLED), authorEntry(team, AuthorStatus.DECLINED)))

        state.offerAtStart()
        awaitIdle()

        assertThat(state.catalog).isNull()
    }

    @Test
    fun manageModeStartsWithNothingSelectedAndTheFilterOff() {
        state.openManage()
        await { state.catalog != null }

        assertThat(state.catalog!!.offer).isFalse()
        assertThat(state.catalog!!.onlyNew).isFalse()
        assertThat(state.catalog!!.selected).isEmpty()
    }

    @Test
    fun installRunsTheCheckedItemsAndClosesTheStartOffer() {
        openStartOffer()

        state.install()

        verify(service, timeout(WAIT_MILLIS)).install(listOf("market", "plugin", "team"))
        await { state.catalog == null }
        assertThat(toasts).containsExactly("Author configuration installed")
    }

    @Test
    fun installInManageModeKeepsTheDialogOpenAndClearsTheChecks() {
        state.openManage()
        await { state.catalog != null }
        state.catalog!!.check("plugin", true)

        state.install()

        verify(service, timeout(WAIT_MILLIS)).install(listOf("market", "plugin"))
        await { state.catalog!!.selected.isEmpty() && !state.busy }
    }

    @Test
    fun installDoesNotDeclineTheUncheckedItems() {
        openStartOffer()
        state.catalog!!.check("team", false)

        state.install()
        await { state.catalog == null }

        verify(service, never()).decline(anyList())
    }

    @Test
    fun declineUncheckedRecordsOnlyTheUncheckedItemsStillOffered() {
        `when`(service.entries()).thenReturn(listOf(authorEntry(market), authorEntry(plugin), authorEntry(team, AuthorStatus.INSTALLED)))
        openStartOffer()
        state.catalog!!.check("plugin", false)

        state.declineUnchecked()

        verify(service, timeout(WAIT_MILLIS)).decline(listOf("plugin"))
    }

    @Test
    fun notNowRecordsNothing() {
        openStartOffer()

        state.closeCatalog()

        assertThat(state.catalog).isNull()
        verify(service, never()).decline(anyList())
        verify(service, never()).install(anyList())
    }
}
