package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.review.toolName
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextArea

private val popoverWidth = 320.dp
private val popoverHeight = 190.dp

/** Comment box floating at the right-click spot for a "Leave feedback" draft: Ctrl+Enter saves, Esc cancels. */
@Composable
fun WebFeedbackPopover(view: WebView, state: WebState) {
    val menu = view.contextMenu?.takeIf { it.feedback } ?: return
    val target = view.picked
    LaunchedEffect(target) { if (target == null) view.contextMenu = null }
    if (target == null) return
    val density = LocalDensity.current
    val cancel = { state.cancelDraft(view); WebContextActions.dismiss(view) }
    val save = { if (view.canSave) { state.saveDraft(view); WebContextActions.dismiss(view) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(view.draftGeneration) { focus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val x = with(density) { menu.canvasOffset.x.toDp() }.coerceIn(0.dp, (maxWidth - popoverWidth).coerceAtLeast(0.dp))
        val y = with(density) { menu.canvasOffset.y.toDp() }.coerceIn(0.dp, (maxHeight - popoverHeight).coerceAtLeast(0.dp))
        Column(
            Modifier.offset(x, y).width(popoverWidth).card(RoundedCornerShape(Radius.md), colors.surfaceRaised).padding(Spacing.md)
                .onKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { cancel(); true } else false },
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Label(view.draftShape?.let { toolName(it.kind.tool()) } ?: target.selector,Modifier.fillMaxWidth(), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
            VoiceTextArea(view.comment, Modifier.fillMaxWidth(), placeholder = "What should change here?", focusRequester = focus, minLines = 2, maxLines = 5, onSubmit = save)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                AppButton("Cancel", cancel, variant = ButtonVariant.GHOST, compact = true)
                AppButton("Add to review", save, variant = ButtonVariant.PRIMARY, enabled = view.canSave && !view.draftSaving, compact = true)
            }
        }
    }
}
