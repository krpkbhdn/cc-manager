package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val barWidth = 160.dp
private val barHeight = 4.dp
private const val PERCENT = 100

/** One download of the voice input with its size and a status-dependent control; [enabled] gates the download buttons. */
@Composable
fun ResourceRow(resource: VoiceResource, enabled: Boolean, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label(resource.name, style = Type.bodyMedium)
            val failed = resource.status as? VoiceResourceStatus.Failed
            Label(failed?.message ?: resource.sizeLabel, color = if (failed != null) colors.danger else colors.textSecondary, style = Type.small, maxLines = 2)
        }
        ResourceStatus(resource, enabled, onDownload)
    }
}

@Composable
private fun ResourceStatus(resource: VoiceResource, enabled: Boolean, onDownload: () -> Unit) {
    when (val status = resource.status) {
        VoiceResourceStatus.Installed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Glyph(Icons.Check, colors.success, size = 14.dp)
            Label("Installed", color = colors.textSecondary, style = Type.small)
        }
        VoiceResourceStatus.Missing -> AppButton("Download", onDownload, icon = Icons.Download, enabled = enabled)
        is VoiceResourceStatus.Downloading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Spinner()
            ProgressBar(status.fraction)
            Label("${(status.fraction * PERCENT).toInt()}%", Modifier.width(36.dp), color = colors.textSecondary, style = Type.small)
        }
        is VoiceResourceStatus.Failed -> AppButton("Retry", onDownload, icon = Icons.Refresh, enabled = enabled)
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(barHeight)
    Box(modifier.width(barWidth).height(barHeight).background(colors.borderStrong, shape)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(colors.accent, shape))
    }
}
