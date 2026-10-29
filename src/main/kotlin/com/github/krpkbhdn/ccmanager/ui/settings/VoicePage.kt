package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.LiveTestBlock
import com.github.krpkbhdn.ccmanager.ui.voice.ResourceRow
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceSettingsState
import com.github.krpkbhdn.ccmanager.ui.voice.humanSize

private const val DEFAULT_INPUT_LABEL = "System default"
private const val ENABLE_LABEL = "Enable voice input"

@Composable
fun VoicePage(app: AppState) {
    val voice = app.voice
    if (voice == null) {
        Label("Voice input is unavailable", color = colors.textSecondary)
        return
    }
    DisposableEffect(voice) { onDispose { voice.stopLiveTest() } }
    SettingGroup("Voice input") {
        SettingRow(ENABLE_LABEL, "Speech is recognised on this computer inside the app. Nothing is sent over the network.") {
            AppSwitch(voice.enabled, voice::setEnabled, Modifier.semantics { contentDescription = ENABLE_LABEL })
        }
    }
    Column(Modifier.alpha(if (voice.enabled) 1f else Alpha.DISABLED), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        DeviceGroup(voice)
        ModelGroup(voice)
        ComponentsGroup(voice)
        LiveTestGroup(voice)
    }
}

@Composable
private fun DeviceGroup(voice: VoiceSettingsState) {
    SettingGroup("Device") {
        SettingRow("Compute device", "Where the model runs. A GPU is much faster when its runtime is installed.") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Dropdown(
                    voice.deviceLabel,
                    voice.deviceChoices.map { choice -> MenuEntry(choice.label) { voice.selectDevice(choice.spec) } },
                    voice.enabled,
                )
                IconButton(Icons.Refresh, voice::refreshDevices, enabled = voice.enabled, tooltip = "Look for devices again")
            }
        }
        if (voice.restartRequired) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Glyph(Icons.Alert, colors.warning, size = 14.dp)
                Label("Switching between CPU and GPU requires restarting the app", color = colors.warning, style = Type.small)
            }
        }
        SettingRow("Microphone", "The input that is listened to.") {
            Dropdown(
                voice.input.ifBlank { DEFAULT_INPUT_LABEL },
                (listOf(DEFAULT_INPUT_LABEL) + voice.inputs).map { name -> MenuEntry(name) { voice.selectInput(if (name == DEFAULT_INPUT_LABEL) "" else name) } },
                voice.enabled,
            )
        }
    }
}

@Composable
private fun ModelGroup(voice: VoiceSettingsState) {
    SettingGroup("Model") {
        SettingRow("Whisper model", "Bigger models understand better and need more memory and time.") {
            Dropdown(
                voice.model.displayName(),
                voice.models.map { model -> MenuEntry(model.displayName(), hint = humanSize(model.asset().sizeBytes())) { voice.selectModel(model) } },
                voice.enabled,
            )
        }
    }
}

@Composable
private fun ComponentsGroup(voice: VoiceSettingsState) {
    SettingGroup("Components") {
        SettingRow("Downloads", "Nothing is downloaded until you ask for it.") {
            AppButton(if (voice.downloading) "Downloading…" else "Download all missing", voice::downloadAllMissing, variant = ButtonVariant.PRIMARY, icon = Icons.Download, enabled = voice.enabled && voice.canDownloadAll)
        }
        voice.resources.forEach { resource -> ResourceRow(resource, voice.enabled && !voice.downloading, { voice.download(resource) }) }
    }
}

@Composable
private fun LiveTestGroup(voice: VoiceSettingsState) {
    SettingGroup("Live test") {
        SettingRow("Try it out", "Speak to check the microphone, model and device. The text is only shown here.") {
            if (voice.liveTestActive) {
                AppButton("Stop", voice::stopLiveTest, variant = ButtonVariant.DANGER, icon = Icons.MicOff)
            } else {
                AppButton("Start listening", voice::startLiveTest, variant = ButtonVariant.PRIMARY, icon = Icons.Mic, enabled = voice.canLiveTest, tooltip = voice.liveTestBlocker)
            }
        }
        LiveTestBlock(voice.liveTest, voice.liveText, selectable = voice.enabled)
    }
}

@Composable
private fun Dropdown(label: String, entries: List<MenuEntry>, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(label, { open = !open }, variant = ButtonVariant.SECONDARY, icon = Icons.ChevronDown, enabled = enabled)
        if (open && enabled) PopupMenu(entries, { open = false }, alignEnd = true)
    }
}
