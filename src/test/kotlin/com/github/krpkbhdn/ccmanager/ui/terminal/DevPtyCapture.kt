package com.github.krpkbhdn.ccmanager.ui.terminal

import com.pty4j.PtyProcessBuilder
import java.io.File
import kotlin.concurrent.thread

/**
 * Dev-only: `DevPtyCapture <outFile> <dir> <step>...` runs the real claude in a PTY, performs the steps and dumps the
 * raw output bytes. A step is `wait:<ms>`, `esc`, `shifttab` or any other text typed verbatim (`\r` = Enter).
 */
fun main(args: Array<String>) {
    val claude = CLAUDE_EXE
    val process = PtyProcessBuilder((System.getenv("CAPTURE_CMD")?.split(" ") ?: listOf(claude)).toTypedArray()).setDirectory(args[1])
        .setEnvironment(cleanDevEnvironment())
        .setUseWinConPty(System.getenv("CAPTURE_WINPTY") == null).setInitialColumns(120).setInitialRows(32).start()
    val out = File(args[0]).outputStream()
    thread(isDaemon = true) {
        val buffer = ByteArray(8192)
        while (true) {
            val n = process.inputStream.read(buffer)
            if (n < 0) break
            synchronized(out) { out.write(buffer, 0, n); out.flush() }
            if (String(buffer, 0, n, Charsets.ISO_8859_1).contains("\u001b[c") && System.getenv("CAPTURE_DA1") != null) {
                process.outputStream.apply { write("\u001b[?1;0c".toByteArray()); flush() }
            }
        }
    }
    for (step in args.drop(2)) {
        val marker = "\n<<STEP $step>>\n".toByteArray()
        synchronized(out) { out.write(marker) }
        when {
            step.startsWith("wait:") -> Thread.sleep(step.removePrefix("wait:").toLong())
            step == "esc" -> process.outputStream.apply { write(0x1b); flush() }
            step == "shifttab" -> process.outputStream.apply { write("\u001b[Z".toByteArray()); flush() }
            else -> process.outputStream.apply { write(step.replace("\\r", "\r").replace("\\e", "\u001b").toByteArray()); flush() }
        }
    }
    process.destroyForcibly()
    Runtime.getRuntime().halt(0)
}
