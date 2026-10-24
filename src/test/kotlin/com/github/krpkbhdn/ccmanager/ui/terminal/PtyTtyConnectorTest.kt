package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PtyTtyConnectorTest {

    @Test
    fun singleEscByteIsEscKey() {
        assertThat(isLoneEscape(byteArrayOf(0x1B))).isTrue()
    }

    @Test
    fun sequencesAndOtherBytesAreNotEscKey() {
        assertThat(isLoneEscape(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()))).isFalse()
        assertThat(isLoneEscape(byteArrayOf(0x1B, 'b'.code.toByte()))).isFalse()
        assertThat(isLoneEscape(byteArrayOf('a'.code.toByte()))).isFalse()
        assertThat(isLoneEscape(byteArrayOf())).isFalse()
    }

    @Test
    fun fontPrefersFirstInstalledPreferredFamily() {
        assertThat(pickFontFamily(setOf("Consolas", "Arial"), listOf("JetBrains Mono", "Consolas"))).isEqualTo("Consolas")
        assertThat(pickFontFamily(setOf("Arial"), listOf("Consolas"))).isEqualTo("Monospaced")
    }
}
