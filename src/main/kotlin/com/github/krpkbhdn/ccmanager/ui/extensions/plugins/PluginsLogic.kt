package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext

private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val TEN = 10L

fun pluginName(pluginId: String): String = pluginId.substringBefore('@')

fun pluginMarketplace(pluginId: String): String = pluginId.substringAfter('@', "")

/** "3.4k" style install counts; always rounds down so a count never reads higher than it is. */
fun compactCount(count: Long): String = when {
    count < THOUSAND -> count.toString()
    count < TEN * THOUSAND -> scaled(count, THOUSAND, "k")
    count < MILLION -> "${count / THOUSAND}k"
    count < TEN * MILLION -> scaled(count, MILLION, "M")
    else -> "${count / MILLION}M"
}

private fun scaled(count: Long, unit: Long, suffix: String): String {
    val tenths = count / (unit / TEN)
    return if (tenths % TEN == 0L) "${tenths / TEN}$suffix" else "${tenths / TEN}.${tenths % TEN}$suffix"
}

/** An installation shown in a context; [inherited] ones come from the user scope and cannot be changed from a project. */
data class InstalledEntry(val plugin: InstalledPlugin, val inherited: Boolean) {
    val name: String get() = pluginName(plugin.id)
    val marketplace: String get() = pluginMarketplace(plugin.id)
}

/** What a context shows: its own installations first, then inherited ones, each group by name. */
fun installedEntries(all: List<InstalledPlugin>, context: ExtensionContext): List<InstalledEntry> =
    all.filter { context.sees(it.scope, it.projectPath) }
        .map { InstalledEntry(it, inherited = !context.owns(it.scope, it.projectPath)) }
        .sortedWith(compareBy<InstalledEntry> { it.inherited }.thenBy { it.name.lowercase() }.thenBy { it.plugin.scope })

enum class CatalogSort(val label: String) { POPULAR("Popular"), NAME("Name") }

data class CatalogQuery(val text: String = "", val marketplace: String? = null, val sort: CatalogSort = CatalogSort.POPULAR)

/** Every word of the query must appear in the plugin's name, description or marketplace. */
fun filterCatalog(catalog: List<AvailablePlugin>, query: CatalogQuery): List<AvailablePlugin> {
    val words = query.text.lowercase().split(' ').filter { it.isNotBlank() }
    val matching = catalog.filter { plugin ->
        (query.marketplace == null || plugin.marketplaceName == query.marketplace) && words.all { plugin.mentions(it) }
    }
    return when (query.sort) {
        CatalogSort.POPULAR -> matching.sortedWith(compareByDescending<AvailablePlugin> { it.installCount }.thenBy { it.name.lowercase() })
        CatalogSort.NAME -> matching.sortedBy { it.name.lowercase() }
    }
}

private fun AvailablePlugin.mentions(word: String): Boolean =
    name.contains(word, ignoreCase = true) || description.contains(word, ignoreCase = true) || marketplaceName.contains(word, ignoreCase = true)
