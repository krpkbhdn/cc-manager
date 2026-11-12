package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

/** The right panel: the inspector for the picked element, or the review items of every revision. */
@Composable
fun WebSidePanel(projectId: Long, view: WebView, state: WebState, modifier: Modifier = Modifier) {
    val tabs = listOf(Segment(WebPanelTab.INSPECTOR, "Inspector"), Segment(WebPanelTab.ITEMS, "Items", count = state.pendingCount(projectId)))
    Column(modifier.fillMaxSize()) {
        SegmentedControl(tabs, view.panelTab, { view.panelTab = it }, Modifier.padding(Spacing.md))
        HorizontalRule()
        when (view.panelTab) {
            WebPanelTab.INSPECTOR -> WebInspectorPanel(view, state)
            WebPanelTab.ITEMS -> WebItemsPanel(projectId, view, state)
        }
    }
}
