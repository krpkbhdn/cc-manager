package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A [TtyConnector] without a process: [feed] plays process output into the emulator, what the terminal writes is collected in [written]. */
internal class ScriptedConnector : TtyConnector {
    private val output = LinkedBlockingQueue<String>()
    private var pending = ""
    val written = CopyOnWriteArrayList<ByteArray>()
    val resizes = CopyOnWriteArrayList<TermSize>()

    @Volatile
    private var closed = false

    fun feed(text: String) = output.put(text)

    fun feedBytes(bytes: ByteArray) = feed(String(bytes, Charsets.UTF_8))

    fun hasUnreadOutput(): Boolean = output.isNotEmpty() || pending.isNotEmpty()

    fun writtenText(): String = written.joinToString("") { String(it, Charsets.ISO_8859_1) }

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        while (pending.isEmpty()) {
            if (closed) return -1
            pending = output.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) ?: ""
        }
        val count = minOf(length, pending.length)
        pending.toCharArray(buf, offset, 0, count)
        pending = pending.substring(count)
        return count
    }

    override fun write(bytes: ByteArray) {
        written.add(bytes)
    }

    override fun write(string: String) = write(string.toByteArray(Charsets.UTF_8))

    override fun isConnected() = !closed

    override fun resize(termSize: TermSize) {
        resizes.add(termSize)
    }

    override fun waitFor(): Int = 0

    override fun ready() = output.isNotEmpty()

    override fun getName() = "scripted"

    override fun close() {
        closed = true
    }

    private companion object {
        const val POLL_MILLIS = 20L
    }
}

private const val SETTLE_POLL_MILLIS = 40L
private const val SETTLE_STABLE_POLLS = 4
private const val SETTLE_LIMIT_MILLIS = 5_000L

/** Waits until the emulator has consumed all fed output and the screen has stopped changing. */
internal fun settle(connector: ScriptedConnector, session: TerminalSession) {
    val deadline = System.currentTimeMillis() + SETTLE_LIMIT_MILLIS
    var stable = 0
    var lastFrame = -1L
    while (stable < SETTLE_STABLE_POLLS && System.currentTimeMillis() < deadline) {
        Thread.sleep(SETTLE_POLL_MILLIS)
        val frame = session.frame
        stable = if (frame == lastFrame && !connector.hasUnreadOutput()) stable + 1 else 0
        lastFrame = frame
    }
}
