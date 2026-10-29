package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VoiceResourceTest {
    @Test
    fun `sizes are shown in the largest fitting unit`() {
        assertThat(humanSize(0)).isEqualTo("0 B")
        assertThat(humanSize(1023)).isEqualTo("1023 B")
        assertThat(humanSize(1024)).isEqualTo("1.0 KB")
        assertThat(humanSize(1536L * 1024)).isEqualTo("1.5 MB")
        assertThat(humanSize(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB")
        assertThat(humanSize(5L * 1024 * 1024 * 1024 * 1024)).isEqualTo("5120.0 GB")
    }

    @Test
    fun `only a missing or failed resource can be downloaded`() {
        val asset = VoiceAsset.SILERO_VAD

        assertThat(VoiceResource(asset, VoiceResourceStatus.Missing).canDownload).isTrue()
        assertThat(VoiceResource(asset, VoiceResourceStatus.Failed("x")).canDownload).isTrue()
        assertThat(VoiceResource(asset, VoiceResourceStatus.Installed).canDownload).isFalse()
        assertThat(VoiceResource(asset, VoiceResourceStatus.Downloading(0.5f)).canDownload).isFalse()
    }

    @Test
    fun `a resource shows the name and size of its asset`() {
        val resource = VoiceResource(VoiceAsset.SILERO_VAD, VoiceResourceStatus.Missing)

        assertThat(resource.name).isEqualTo(VoiceAsset.SILERO_VAD.name())
        assertThat(resource.sizeLabel).isEqualTo(humanSize(VoiceAsset.SILERO_VAD.sizeBytes()))
    }
}
