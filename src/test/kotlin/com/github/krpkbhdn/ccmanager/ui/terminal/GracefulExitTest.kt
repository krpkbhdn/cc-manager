package com.github.krpkbhdn.ccmanager.ui.terminal

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val GRACE_MILLIS = 300L
private const val UNUSED_PID = Int.MAX_VALUE.toLong()

class GracefulExitTest {

    @Test
    fun aProcessThatExitsWithinTheGraceIsNotKilled() {
        val process = FakeProcess()
        process.exitAfter(50)
        killUnlessExited(listOf(process), GRACE_MILLIS)
        assertThat(process.killed).isFalse()
    }

    @Test
    fun aProcessStillAliveAfterTheGraceIsKilled() {
        val process = FakeProcess()
        killUnlessExited(listOf(process), GRACE_MILLIS)
        assertThat(process.killed).isTrue()
    }

    @Test
    fun allProcessesShareOneGracePeriod() {
        val stuck = List(3) { FakeProcess() }
        val started = System.nanoTime()
        killUnlessExited(stuck, GRACE_MILLIS)
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(GRACE_MILLIS * 2)
        assertThat(stuck).allMatch { it.killed }
    }

    private class FakeProcess : Process() {
        private val exited = CountDownLatch(1)

        @Volatile
        var killed = false

        fun exitAfter(millis: Long) {
            Thread { Thread.sleep(millis); exited.countDown() }.start()
        }

        override fun pid() = UNUSED_PID
        override fun waitFor(timeout: Long, unit: TimeUnit) = exited.await(timeout, unit)
        override fun waitFor(): Int = exited.await().let { 0 }
        override fun exitValue(): Int = if (exited.count == 0L) 0 else throw IllegalThreadStateException()
        override fun destroy() = destroyForcibly().let { }
        override fun destroyForcibly(): Process = also { killed = true; exited.countDown() }
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
    }
}
