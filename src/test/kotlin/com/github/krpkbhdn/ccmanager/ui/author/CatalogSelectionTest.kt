package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorArea
import com.github.krpkbhdn.ccmanager.author.AuthorCatalog
import com.github.krpkbhdn.ccmanager.author.AuthorItem
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogSelectionTest {
    private fun entry(item: AuthorItem, status: AuthorStatus = AuthorStatus.NOT_INSTALLED) = authorEntry(item, status)

    private val market = authorItem("market", group = "Marketplaces", title = "Market")
    private val pluginA = authorItem("a", requires = listOf("market"), title = "Alpha", description = "Finds duplicate code")
    private val pluginB = authorItem("b", requires = listOf("market"), title = "Beta")
    private val chained = authorItem("c", requires = listOf("a"), title = "Gamma")
    private val settings = authorItem("s", area = AuthorArea.CLAUDE_CONFIG, group = "Settings", title = "Attribution")
    private val team = authorItem("t", area = AuthorArea.CLAUDE_CONFIG, group = "Teams")
    private val catalog = AuthorCatalog(listOf(market, pluginA, pluginB, chained, settings, team), emptyList())
    private val entries = catalog.items().map { entry(it) }

    @Test
    fun checkingAnItemChecksWhatItRequiresTransitively() {
        assertThat(CatalogSelection.check(catalog, emptySet(), "c", true)).containsExactlyInAnyOrder("c", "a", "market")
    }

    @Test
    fun checkingSkipsRequirementsThatAreAlreadyInstalled() {
        assertThat(CatalogSelection.check(catalog, emptySet(), "c", true, installed = setOf("market"))).containsExactlyInAnyOrder("c", "a")
    }

    @Test
    fun uncheckingARequirementUnchecksEverythingThatNeedsIt() {
        val all = setOf("market", "a", "b", "c", "s")

        assertThat(CatalogSelection.check(catalog, all, "market", false)).containsExactly("s")
        assertThat(CatalogSelection.check(catalog, all, "a", false)).containsExactlyInAnyOrder("market", "b", "s")
    }

    @Test
    fun uncheckingALeafKeepsItsRequirement() {
        assertThat(CatalogSelection.check(catalog, setOf("market", "a"), "a", false)).containsExactly("market")
    }

    @Test
    fun checkAllAndNoneCoverTheGivenIds() {
        val on = CatalogSelection.checkAll(catalog, emptySet(), listOf("a", "b"), true)
        assertThat(on).containsExactlyInAnyOrder("a", "b", "market")
        assertThat(CatalogSelection.checkAll(catalog, on, listOf("market", "a", "b"), false)).isEmpty()
    }

    @Test
    fun triStateTellsNoneSomeAndAll() {
        val ids = listOf("a", "b")

        assertThat(CatalogSelection.triState(emptySet(), ids)).isEqualTo(Check.NONE)
        assertThat(CatalogSelection.triState(setOf("a", "x"), ids)).isEqualTo(Check.SOME)
        assertThat(CatalogSelection.triState(setOf("a", "b"), ids)).isEqualTo(Check.ALL)
        assertThat(CatalogSelection.triState(emptySet(), emptyList())).isEqualTo(Check.NONE)
    }

    @Test
    fun onlyInstalledItemsAreNotSelectable() {
        val all = AuthorStatus.entries.associateWith { CatalogSelection.selectable(entry(market, it)) }

        assertThat(all).containsEntry(AuthorStatus.INSTALLED, false).containsEntry(AuthorStatus.DECLINED, true)
            .containsEntry(AuthorStatus.NOT_INSTALLED, true).containsEntry(AuthorStatus.UPDATE_AVAILABLE, true)
    }

    @Test
    fun alsoInstallsNamesWhatIsRequiredAndNotInstalledYet() {
        assertThat(CatalogSelection.alsoInstalls(catalog, "c", emptySet())).containsExactly("Market", "Alpha")
        assertThat(CatalogSelection.alsoInstalls(catalog, "c", setOf("market"))).containsExactly("Alpha")
        assertThat(CatalogSelection.alsoInstalls(catalog, "market", emptySet())).isEmpty()
        assertThat(CatalogSelection.alsoInstalls(catalog, "unknown", emptySet())).isEmpty()
    }

    @Test
    fun withoutAQueryOnlyTheTabIsListed() {
        assertThat(CatalogSelection.visible(entries, CatalogTab.TOOLS, "", false).map { it.item().id() }).containsExactly("market", "a", "b", "c")
        assertThat(CatalogSelection.visible(entries, CatalogTab.CONFIG, "", false).map { it.item().id() }).containsExactly("s", "t")
    }

    @Test
    fun aQuerySearchesEveryAreaByTitleDescriptionAndGroup() {
        assertThat(CatalogSelection.visible(entries, CatalogTab.CONFIG, "duplicate", false).map { it.item().id() }).containsExactly("a")
        assertThat(CatalogSelection.visible(entries, CatalogTab.TOOLS, " ATTRIBUTION ", false).map { it.item().id() }).containsExactly("s")
        assertThat(CatalogSelection.visible(entries, CatalogTab.TOOLS, "teams", false).map { it.item().id() }).containsExactly("t")
    }

    @Test
    fun onlyNewKeepsItemsWaitingForADecision() {
        val mixed = listOf(
            entry(market, AuthorStatus.INSTALLED), entry(pluginA, AuthorStatus.UPDATE_AVAILABLE),
            entry(pluginB, AuthorStatus.DECLINED), entry(chained, AuthorStatus.NOT_INSTALLED),
        )

        assertThat(CatalogSelection.visible(mixed, CatalogTab.TOOLS, "", true).map { it.item().id() }).containsExactly("a", "c")
    }

    @Test
    fun groupsKeepCatalogOrder() {
        val groups = CatalogSelection.groups(CatalogSelection.visible(entries, CatalogTab.TOOLS, "", false), false)

        assertThat(groups.map { it.title }).containsExactly("Marketplaces", "Plugins")
    }

    @Test
    fun searchTitlesCarryTheAreaOnlyWhenTheMatchesSpanBothAreas() {
        val one = CatalogSelection.groups(CatalogSelection.visible(entries, CatalogTab.TOOLS, "attribution", false), true)
        val both = CatalogSelection.groups(CatalogSelection.visible(entries, CatalogTab.TOOLS, "t", false), true)

        assertThat(one.map { it.title }).containsExactly("Settings")
        assertThat(both.map { it.title }).contains("Claude tools: Marketplaces", "Configuration: Settings")
    }

    @Test
    fun everyGroupIsFollowedByItsCards() {
        val groups = CatalogSelection.groups(CatalogSelection.visible(entries, CatalogTab.TOOLS, "", false), false)

        assertThat(CatalogSelection.rows(groups).map { it::class.simpleName }).containsExactly("Header", "Card", "Header", "Card", "Card", "Card")
    }

    @Test
    fun availableCountsNewAndUpdatableItemsPerAreaAndGroup() {
        val mixed = listOf(entry(market, AuthorStatus.INSTALLED), entry(pluginA, AuthorStatus.UPDATE_AVAILABLE), entry(pluginB, AuthorStatus.DECLINED), entry(settings))

        assertThat(CatalogSelection.available(mixed, CatalogTab.TOOLS)).isEqualTo(1)
        assertThat(CatalogSelection.available(mixed, CatalogTab.CONFIG)).isEqualTo(1)
        assertThat(CatalogSelection.available(CatalogGroup("k", "Plugins", mixed.subList(1, 3)))).isEqualTo(1)
    }

    @Test
    fun theFirstTabWithNewsOpensAndTheToolsTabIsTheFallback() {
        assertThat(CatalogSelection.firstTabWithNews(listOf(entry(market, AuthorStatus.INSTALLED), entry(settings)))).isEqualTo(CatalogTab.CONFIG)
        assertThat(CatalogSelection.firstTabWithNews(listOf(entry(market, AuthorStatus.INSTALLED)))).isEqualTo(CatalogTab.TOOLS)
    }

    @Test
    fun declineTargetsUncheckedNewAndUpdatedItemsOnly() {
        val mixed = listOf(entry(market, AuthorStatus.INSTALLED), entry(pluginA, AuthorStatus.UPDATE_AVAILABLE), entry(pluginB), entry(chained, AuthorStatus.DECLINED))

        assertThat(CatalogSelection.toDecline(mixed, setOf("b")).map { it.item().id() }).containsExactly("a")
    }

    @Test
    fun theEmptyMessageTellsWhyNothingIsListed() {
        assertThat(CatalogSelection.emptyMessage(searching = true, onlyNew = true)).isEqualTo("No items match your search.")
        assertThat(CatalogSelection.emptyMessage(searching = false, onlyNew = true)).contains("Nothing new or updated")
    }
}
