package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val WINDOW_SHARE = 0.88f
private val dialogWidth = 880.dp
private val navWidth = 216.dp

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.APPEARANCE -> Icons.Sun
    SettingsCategory.NOTIFICATIONS -> Icons.Bell
    SettingsCategory.SESSIONS -> Icons.Layers
    SettingsCategory.USAGE -> Icons.Gauge
    SettingsCategory.ASSISTANT -> Icons.Sparkles
    SettingsCategory.VOICE -> Icons.Mic
    SettingsCategory.UPDATES -> Icons.Download
    SettingsCategory.AUTHOR -> Icons.User
    SettingsCategory.ABOUT -> Icons.Info
}

/** IDE-style app settings: category list on the left, page of the selected one on the right; changes apply at once. */
@Composable
fun SettingsDialog(app: AppState) {
    val state = app.settingsDialog
    BoxWithConstraints {
        ModalHost(state::close, width = minOf(dialogWidth, maxWidth * WINDOW_SHARE)) {
            Column(
                Modifier.height(maxHeight * WINDOW_SHARE).onPreviewKeyEvent { event ->
                    val delta = when (event.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> 0 }
                    (delta != 0 && event.type == KeyEventType.KeyDown).also { if (it) state.move(delta) }
                },
            ) {
                SettingsHeader(state.category)
                HorizontalRule()
                Row(Modifier.weight(1f)) {
                    CategoryList(state)
                    VerticalRule()
                    CategoryPage(app, state.category, Modifier.weight(1f).fillMaxHeight())
                }
                HorizontalRule()
                Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.End) { AppButton("Close", state::close) }
            }
        }
    }
}

@Composable
private fun SettingsHeader(category: SettingsCategory) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Glyph(Icons.Settings, colors.textSecondary, size = IconSize.large)
        Label("Settings  /  ${category.label}", style = Type.subtitle)
    }
}

@Composable
private fun CategoryList(state: SettingsState) {
    Column(Modifier.width(navWidth).fillMaxHeight().background(colors.sidebar).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        SettingsCategory.entries.forEach { CategoryRow(it, state.category == it) { state.select(it) } }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, selected: Boolean, onClick: () -> Unit) {
    val tint: Color = if (selected) colors.accent else colors.textSecondary
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (selected) colors.accentSoft else Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Glyph(category.icon(), tint, size = IconSize.large)
            Label(category.label, Modifier.weight(1f), color = if (selected) colors.textPrimary else colors.textSecondary, style = Type.bodyMedium)
        }
    }
}

@Composable
private fun CategoryPage(app: AppState, category: SettingsCategory, modifier: Modifier) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            when (category) {
                SettingsCategory.APPEARANCE -> AppearancePage(app)
                SettingsCategory.NOTIFICATIONS -> NotificationsPage(app)
                SettingsCategory.SESSIONS -> SessionsPage(app)
                SettingsCategory.USAGE -> UsagePage(app)
                SettingsCategory.ASSISTANT -> AssistantPage(app)
                SettingsCategory.VOICE -> VoicePage(app)
                SettingsCategory.UPDATES -> UpdatesPage(app.update)
                SettingsCategory.AUTHOR -> AuthorPage(app)
                SettingsCategory.ABOUT -> AboutPage(app)
            }
        }
        ThinScrollbar(scroll)
    }
}
