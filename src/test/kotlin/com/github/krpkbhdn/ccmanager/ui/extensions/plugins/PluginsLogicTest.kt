package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PluginsLogicTest {
    private val project = Path.of("C:/work/app")
    private val other = Path.of("C:/work/other")

    private fun installed(id: String, scope: ExtensionScope, path: Path? = null) = InstalledPlugin(id, "1.0.0", scope, true, Path.of("x"), path)

    private fun available(name: String, market: String, description: String, count: Long) = AvailablePlugin("$name@$market", name, description, market, count)

    @Test
    fun compactCountRoundsDownToOneDecimal() {
        assertThat(listOf(0L, 950L, 1_000L, 3_450L, 9_999L, 10_000L, 412_999L, 1_250_000L, 12_000_000L).map(::compactCount))
            .containsExactly("0", "950", "1k", "3.4k", "9.9k", "10k", "412k", "1.2M", "12M")
    }

    @Test
    fun globalContextShowsOnlyUserInstallations() {
        val all = listOf(installed("a@m", ExtensionScope.USER), installed("b@m", ExtensionScope.PROJECT, project))

        assertThat(installedEntries(all, ExtensionContext.Global).map { it.name }).containsExactly("a")
    }

    @Test
    fun projectContextShowsItsOwnFirstThenInheritedUserOnes() {
        val context = ExtensionContext.ForProject(1, "app", project)
        val all = listOf(
            installed("zeta@m", ExtensionScope.PROJECT, project),
            installed("alpha@m", ExtensionScope.USER),
            installed("beta@m", ExtensionScope.LOCAL, project),
            installed("gamma@m", ExtensionScope.PROJECT, other),
        )

        val entries = installedEntries(all, context)

        assertThat(entries.map { it.name }).containsExactly("beta", "zeta", "alpha")
        assertThat(entries.map { it.inherited }).containsExactly(false, false, true)
    }

    @Test
    fun projectPathsAreComparedNormalised() {
        val context = ExtensionContext.ForProject(1, "app", Path.of("C:/work/app/../app"))

        assertThat(context.owns(ExtensionScope.PROJECT, project)).isTrue()
        assertThat(context.owns(ExtensionScope.PROJECT, other)).isFalse()
        assertThat(context.owns(ExtensionScope.USER, null)).isFalse()
    }

    @Test
    fun catalogueFilterRequiresEveryWordInNameDescriptionOrMarketplace() {
        val catalog = listOf(
            available("docker-kit", "official", "Build images", 5),
            available("sql-helper", "acme", "Query Postgres from docker", 9),
            available("docs", "official", "Writing", 1),
        )

        assertThat(filterCatalog(catalog, CatalogQuery(text = "docker")).map { it.name }).containsExactly("sql-helper", "docker-kit")
        assertThat(filterCatalog(catalog, CatalogQuery(text = "docker acme")).map { it.name }).containsExactly("sql-helper")
        assertThat(filterCatalog(catalog, CatalogQuery(text = "DOCS")).map { it.name }).containsExactly("docs")
    }

    @Test
    fun catalogueFilterNarrowsToOneMarketplaceAndSorts() {
        val catalog = listOf(
            available("b", "official", "", 5),
            available("a", "official", "", 5),
            available("c", "acme", "", 50),
        )

        assertThat(filterCatalog(catalog, CatalogQuery(marketplace = "official")).map { it.name }).containsExactly("a", "b")
        assertThat(filterCatalog(catalog, CatalogQuery(sort = CatalogSort.POPULAR)).map { it.name }).containsExactly("c", "a", "b")
        assertThat(filterCatalog(catalog, CatalogQuery(sort = CatalogSort.NAME)).map { it.name }).containsExactly("a", "b", "c")
    }

    @Test
    fun pluginIdSplitsIntoNameAndMarketplace() {
        assertThat(pluginName("code-review@official")).isEqualTo("code-review")
        assertThat(pluginMarketplace("code-review@official")).isEqualTo("official")
        assertThat(pluginMarketplace("local-only")).isEmpty()
    }
}
