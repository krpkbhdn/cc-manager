package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionCommandWriterTest {
    private val events = mutableListOf<String>()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) { events += "$ccmSessionId:" + text.replace("\r", "<CR>").replace("\u001b", "<ESC>") }
        override fun close(ccmSessionId: String) = Unit

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val writer = SessionCommandWriter(terminal) { events += "pause:$it" }

    @Test
    fun commandTextAndEnterAreSeparateWritesWithAPauseBetween() = runBlocking {
        writer.run("s1", "/model sonnet")

        assertThat(events).containsExactly("s1:/model sonnet", "pause:700", "s1:<CR>")
    }

    @Test
    fun modelPickerIsAltPAsEscapeP() {
        writer.openModelPicker("s1")

        assertThat(events).containsExactly("s1:<ESC>p")
    }
}
