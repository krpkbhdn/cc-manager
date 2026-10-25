package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorInstallReport
import com.github.krpkbhdn.ccmanager.author.AuthorProfileService
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.author.SessionHandoff
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private const val WAIT_MILLIS = 5_000L
private const val SETTLE_MILLIS = 200L
private const val PROJECT_ID = 7L

class AuthorProfileStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(AuthorProfileService::class.java)
    private val project = Project(PROJECT_ID, "demo", Path.of("demo"), Instant.EPOCH, ModelSelection.DEFAULT)
    private var sessionProject: Project? = project
    private var sessionStarts = true
    private val launched = CopyOnWriteArrayList<Pair<Long, SessionHandoff>>()
    private val toasts = CopyOnWriteArrayList<String>()
    private val errors = CopyOnWriteArrayList<String>()
    private val state = AuthorProfileState(
        service, scope, { sessionProject }, { id -> project.takeIf { id == PROJECT_ID } },
        { id, handoff, onStarted -> launched += id to handoff; if (sessionStarts) onStarted() },
        { error("no sign-in expected") }, { errors += it }, { _: NotificationKind, title: String, _: String -> toasts += title },
    )

    private val user = entry(AuthorComponent.USER_SETUP)
    private val teams = entry(AuthorComponent.TEAMS)
    private val projectSetup = entry(AuthorComponent.PROJECT_SETUP, projectId = PROJECT_ID)

    @AfterEach
    fun stop() {
        scope.cancel()
        assertThat(errors).isEmpty()
    }

    private fun entry(component: AuthorComponent, status: AuthorStatus = AuthorStatus.NOT_INSTALLED, projectId: Long = 0) =
        AuthorEntry(component, projectId, component.name, "summary", 1, 0, status)

    private fun report(withHandoff: Boolean, awaiting: List<AuthorEntry> = emptyList()) =
        AuthorInstallReport(emptyList(), if (withHandoff) Optional.of(SessionHandoff("go", Path.of("profile"))) else Optional.empty(), awaiting)

    @Test
    fun startOffersOnlyComponentsThatAreNewOrHaveAnUpdate() {
        `when`(service.entries(null)).thenReturn(listOf(user, entry(AuthorComponent.TEAMS, AuthorStatus.DECLINED)))

        state.offerAtStart()
        await { state.offer != null }

        assertThat(state.offer!!.entries).containsExactly(user)
    }

    @Test
    fun startShowsNothingWhenEverythingIsDecided() {
        `when`(service.entries(null)).thenReturn(listOf(entry(AuthorComponent.USER_SETUP, AuthorStatus.INSTALLED)))

        state.offerAtStart()
        Thread.sleep(SETTLE_MILLIS)

        assertThat(state.offer).isNull()
    }

    @Test
    fun aProjectIsOfferedItsOwnSetupOncePerRun() {
        `when`(service.entries(PROJECT_ID)).thenReturn(listOf(user, teams, projectSetup))

        state.offerForProject(PROJECT_ID)
        await { state.offer != null }
        assertThat(state.offer!!.entries).containsExactly(projectSetup)
        state.closeOffer()
        state.offerForProject(PROJECT_ID)
        Thread.sleep(SETTLE_MILLIS)

        assertThat(state.offer).isNull()
        verify(service).entries(PROJECT_ID)
    }

    @Test
    fun aProjectIsNotOfferedWhileAnAuthorDialogIsOpen() {
        `when`(service.entries(null)).thenReturn(listOf(user))
        `when`(service.entries(PROJECT_ID)).thenReturn(listOf(projectSetup))
        state.offerAtStart()
        await { state.offer != null }

        state.offerForProject(PROJECT_ID)
        Thread.sleep(SETTLE_MILLIS)

        assertThat(state.offer!!.entries).containsExactly(user)
        verify(service, never()).entries(PROJECT_ID)
    }

    @Test
    fun installDeclinesTheUncheckedBoxesAndInstallsTheCheckedOnesInTheSessionProject() {
        `when`(service.entries(null)).thenReturn(listOf(user, teams))
        `when`(service.install(anyList(), any())).thenReturn(report(true))
        state.offerAtStart()
        await { state.offer != null }
        state.offer!!.checked[AuthorComponent.TEAMS] = false

        state.install(state.offer!!.chosen(), state.offer!!.unchosen())
        await { launched.isNotEmpty() }

        verify(service).decline(listOf(teams))
        verify(service).install(listOf(user), project.path)
        assertThat(launched.single().first).isEqualTo(PROJECT_ID)
        assertThat(state.offer).isNull()
    }

    @Test
    fun projectOfferThatArrivesDuringTheStartOfferIsSkippedAndOfferedOnTheNextVisit() {
        `when`(service.entries(null)).thenAnswer { Thread.sleep(SETTLE_MILLIS); listOf(user) }
        `when`(service.entries(PROJECT_ID)).thenReturn(listOf(projectSetup))
        state.offerAtStart()

        state.offerForProject(PROJECT_ID)
        await { state.offer != null }
        assertThat(state.offer!!.entries).containsExactly(user)
        state.closeOffer()
        state.offerForProject(PROJECT_ID)
        await { state.offer != null }

        assertThat(state.offer!!.entries).containsExactly(projectSetup)
    }

    @Test
    fun teamUpdateStartsUncheckedWhileNewComponentsStartChecked() {
        val update = entry(AuthorComponent.TEAMS, AuthorStatus.UPDATE_AVAILABLE)
        `when`(service.entries(null)).thenReturn(listOf(user, update))

        state.offerAtStart()
        await { state.offer != null }

        assertThat(state.offer!!.chosen()).containsExactly(user)
        assertThat(state.offer!!.unchosen()).containsExactly(update)
    }

    @Test
    fun setupIsRecordedOnlyOnceTheSessionStarted() {
        `when`(service.install(anyList(), any())).thenReturn(report(true, listOf(user)))

        state.install(listOf(user))

        verify(service, timeout(WAIT_MILLIS)).markInstalled(listOf(user))
    }

    @Test
    fun setupIsNotRecordedWhenNoSessionStarted() {
        sessionStarts = false
        `when`(service.install(anyList(), any())).thenReturn(report(true, listOf(user)))

        state.install(listOf(user))
        await { launched.isNotEmpty() }
        Thread.sleep(SETTLE_MILLIS)

        verify(service, never()).markInstalled(anyList())
    }

    @Test
    fun projectSetupForAMissingProjectAbortsWithoutFallingBackToAnotherProject() {
        val gone = entry(AuthorComponent.PROJECT_SETUP, projectId = PROJECT_ID + 1)

        state.install(listOf(gone), listOf(teams))
        await { toasts.isNotEmpty() }

        assertThat(toasts.single()).isEqualTo("Project not found")
        verify(service, never()).decline(anyList())
        verify(service, never()).install(anyList(), any())
    }

    @Test
    fun declineRecordsEveryOfferedComponent() {
        `when`(service.entries(null)).thenReturn(listOf(user, teams))
        state.offerAtStart()
        await { state.offer != null }

        state.declineAll()

        await { state.offer == null }
        verify(service, timeout(WAIT_MILLIS)).decline(listOf(user, teams))
    }

    @Test
    fun laterRecordsNothing() {
        `when`(service.entries(null)).thenReturn(listOf(user))
        state.offerAtStart()
        await { state.offer != null }

        state.closeOffer()

        assertThat(state.offer).isNull()
        verify(service, never()).decline(anyList())
        verify(service, never()).install(anyList(), any())
    }

    @Test
    fun withoutAProjectNothingIsRecorded() {
        sessionProject = null

        state.install(listOf(user), listOf(teams))
        await { toasts.isNotEmpty() }

        assertThat(toasts.single()).isEqualTo("Add a project first")
        verify(service, never()).decline(anyList())
        verify(service, never()).install(anyList(), any())
    }

    @Test
    fun teamsAloneInstallWithoutAProjectAndWithoutASession() {
        sessionProject = null
        `when`(service.install(listOf(teams), null)).thenReturn(report(false))

        state.install(listOf(teams))
        await { toasts.isNotEmpty() }

        assertThat(launched).isEmpty()
    }

    @Test
    fun configurationDialogListsDeclinedComponentsAndCanInstallThem() {
        val declined = entry(AuthorComponent.USER_SETUP, AuthorStatus.DECLINED)
        `when`(service.entries(null)).thenReturn(listOf(declined))
        `when`(service.install(anyList(), any())).thenReturn(report(true))
        state.openManage()
        await { state.manage != null }

        state.install(state.manage!!.entries)
        await { launched.isNotEmpty() }

        verify(service).install(listOf(declined), project.path)
    }
}
