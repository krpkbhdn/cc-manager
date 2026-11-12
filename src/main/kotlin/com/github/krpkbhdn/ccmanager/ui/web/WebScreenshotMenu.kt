package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.menuSize
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.web.browser.ShotArea

/** The three screenshot choices; the hint is the plain key that does the same. */
internal fun screenshotEntries(enabled: Boolean, perform: (WebAction) -> Unit): List<MenuEntry> = listOf(
    MenuEntry("Visible part", hint = "S", enabled = enabled) { perform(WebAction.Screenshot(ShotArea.Visible)) },
    MenuEntry("Whole page", hint = "Shift+S", enabled = enabled) { perform(WebAction.Screenshot(ShotArea.WholePage)) },
    MenuEntry("Region...", hint = "R", enabled = enabled) { perform(WebAction.StartRegion) },
)

/** Camera button: a menu of what to photograph and send to the Review tab; disabled on a blank page and while a capture runs. */
@Composable
fun WebScreenshotMenu(view: WebView, state: WebState, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Icons.Camera, { open = !open }, tooltip = "Screenshot to Review", enabled = enabled, active = view.shooting || view.regionSelecting)
        if (open) AnchoredPopup({ open = false }, modifier = Modifier.menuSize()) {
            MenuColumn(screenshotEntries(enabled) { state.perform(view, it) }, onDismiss = { open = false })
        }
    }
}
