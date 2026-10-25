package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.BusyLabel
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionFailureStrip
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionToolbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

enum class PluginsTab(val label: String) { INSTALLED("Installed"), MARKETPLACE("Marketplace"), SOURCES("Sources") }

/** Plugins and marketplaces for a [context]; reusable on the global screen and, later, in a project's settings. */
@Composable
fun PluginsPage(context: ExtensionContext, state: PluginsState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { state.ensureLoaded() }
    val entries = remember(state.installed, context) { installedEntries(state.installed, context) }
    val segments = PluginsTab.entries.map { Segment(it, it.label, count = when (it) {
        PluginsTab.INSTALLED -> entries.size
        PluginsTab.SOURCES -> state.marketplaces.size
        PluginsTab.MARKETPLACE -> 0
    }) }
    Column(modifier.fillMaxSize()) {
        ExtensionToolbar {
            SegmentedControl(segments, state.tab, onSelect = { state.tab = it })
            Box(Modifier.weight(1f))
            val busy = state.job?.label ?: if (state.loading) "Refreshing" else null
            if (busy != null) BusyLabel(busy)
            IconButton(Icons.Refresh, { state.refresh() }, tooltip = "Refresh", enabled = !state.loading && state.job == null)
        }
        (state.failure ?: state.loadFailure)?.let { ExtensionFailureStrip(it, onDismiss = { if (state.failure != null) state.failure = null else state.dismissLoadFailure() }) }
        Box(Modifier.weight(1f)) {
            when (state.tab) {
                PluginsTab.INSTALLED -> InstalledView(context, state, entries, { state.tab = PluginsTab.MARKETPLACE })
                PluginsTab.MARKETPLACE -> MarketplaceView(context, state, entries)
                PluginsTab.SOURCES -> SourcesView(state)
            }
        }
    }
}
