package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace

/** One row of the plugin picker; [added] means the preset already has it. */
data class PickerPlugin(val id: String, val name: String, val description: String, val marketplace: String, val added: Boolean)

data class PluginGroup(val marketplace: String, val plugins: List<PickerPlugin>)

/**
 * Every plugin the marketplaces offer plus the installed ones they no longer list, grouped by marketplace and sorted by name;
 * [query] keeps the plugins whose name, description or marketplace holds every word.
 */
fun pluginGroups(available: List<AvailablePlugin>, installedIds: Collection<String>, taken: Set<String>, query: String): List<PluginGroup> {
    val offered = available.associateBy { it.pluginId() }
    val extra = installedIds.filter { it !in offered && '@' in it }
    val all = available.map { PickerPlugin(it.pluginId(), it.name(), it.description().orEmpty(), it.marketplaceName(), it.pluginId() in taken) } +
        extra.map { PickerPlugin(it, it.substringBefore('@'), "", it.substringAfter('@'), it in taken) }
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    return all.distinctBy { it.id }
        .filter { plugin -> words.all { it in (plugin.name + " " + plugin.description + " " + plugin.marketplace).lowercase() } }
        .groupBy { it.marketplace }
        .map { (marketplace, plugins) -> PluginGroup(marketplace, plugins.sortedBy { it.name.lowercase() }) }
        .sortedBy { it.marketplace.lowercase() }
}

/** The known marketplaces the chosen plugins come from that the preset does not list yet, one each. */
fun marketplacesToAdd(pluginIds: Collection<String>, inPreset: Set<String>, known: List<Marketplace>): List<Marketplace> {
    val needed = pluginIds.map { it.substringAfter('@') }.toSet() - inPreset
    return known.filter { it.name() in needed }.distinctBy { it.name() }
}
