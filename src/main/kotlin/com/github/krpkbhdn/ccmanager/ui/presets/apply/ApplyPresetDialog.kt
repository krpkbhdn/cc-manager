package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val maxDialogWidth = 880.dp
private val maxDialogHeight = 680.dp
private const val WIDTH_SHARE = 0.92f
private const val HEIGHT_SHARE = 0.86f
private val iconBox = 40.dp

/** Apply preset: choose, review and result steps in one card of up to 880 x 680dp; each step's list scrolls on its own. */
@Composable
fun ApplyPresetDialog(state: ApplyPresetState) {
    val project = state.project ?: return
    BoxWithConstraints {
        ModalHost(state::close, width = minOf(maxDialogWidth, maxWidth * WIDTH_SHARE), height = minOf(maxDialogHeight, maxHeight * HEIGHT_SHARE), hasInput = { state.running }) {
            Column(Modifier.fillMaxSize()) {
                ApplyHeader(project, state.step)
                HorizontalRule()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.step) {
                        ApplyStep.CHOOSE -> ApplyChoosePage(state)
                        ApplyStep.REVIEW -> ApplyReviewPage(state)
                        ApplyStep.RESULT -> ApplyResultPage(state)
                    }
                }
                HorizontalRule()
                ApplyFooter(state)
            }
        }
    }
}

@Composable
private fun ApplyHeader(project: Project, current: ApplyStep) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(iconBox).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
            Glyph(Icons.Tasks, colors.accent, size = IconSize.xlarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label("Apply preset", style = Type.subtitle)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Label(project.name, Modifier.weight(1f, fill = false), color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
                Label(project.path.toString(), Modifier.weight(1f, fill = false), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
            }
        }
        StepIndicator(current)
    }
}

@Composable
private fun StepIndicator(current: ApplyStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        ApplyStep.entries.forEach { step ->
            val on = step == current
            Label("${step.ordinal + 1}  ${step.label}", color = if (on) colors.accent else colors.textTertiary, style = if (on) Type.captionMedium else Type.caption)
        }
    }
}

@Composable
private fun ApplyFooter(state: ApplyPresetState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Label(state.error.orEmpty(), Modifier.weight(1f), color = colors.danger, style = Type.small, maxLines = 2, tooltipWhenTruncated = true)
        when (state.step) {
            ApplyStep.CHOOSE -> {
                AppButton("Cancel", state::close)
                AppButton("Review changes", state::review, variant = ButtonVariant.PRIMARY, enabled = state.presetId != null)
            }
            ApplyStep.REVIEW -> {
                AppButton("Back", state::back, enabled = !state.running)
                AppButton("Cancel", state::close, enabled = !state.running)
                AppButton(applyLabel(state.selection.size), state::apply, variant = ButtonVariant.PRIMARY, enabled = canApply(state.plan, state.selection), loading = state.running)
            }
            ApplyStep.RESULT -> AppButton("Close", state::close, variant = ButtonVariant.PRIMARY)
        }
    }
}
