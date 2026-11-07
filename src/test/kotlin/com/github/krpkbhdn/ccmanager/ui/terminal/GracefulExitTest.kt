package com.github.krpkbhdn.ccmanager.ui.terminal

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val GRACE_MILLIS = 100L
private const val LONG_GRACE_MILLIS = 60_000L
private const val UNUSED_PID = Int.MAX_VALUE.toLong()

class GracefulExitTest {

    @Test
    fun aProcessThatExitsWithinTheGraceIsNotKilled() {
        val process = FakeProcess()
        process.exit()
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
    fun aProcessThatExitsWhileTheGraceIsRunningIsNotKilled() {
        val process = FakeProcess()
        val killer = Thread { killUnlessExited(listOf(process), LONG_GRACE_MILLIS) }
        killer.start()
        assertThat(process.waiting.await(5, TimeUnit.SECONDS)).isTrue()

        process.exit()

        killer.join(5_000)
        assertThat(killer.isAlive).isFalse()
        assertThat(process.killed).isFalse()
    }

    @Test
    fun allProcessesShareOneGracePeriod() {
        val stuck = List(3) { FakeProcess() }

        killUnlessExited(stuck, GRACE_MILLIS)

        assertThat(stuck.sumOf { it.requestedWaitNanos }).isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(GRACE_MILLIS))
        assertThat(stuck).allMatch { it.killed }
    }

    private class FakeProcess : Process() {
        private val exited = CountDownLatch(1)
        val waiting = CountDownLatch(1)

        @Volatile
        var requestedWaitNanos = 0L

        @Volatile
        var killed = false

        fun exit() = exited.countDown()

        override fun pid() = UNUSED_PID
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            requestedWaitNanos += unit.toNanos(timeout)
            waiting.countDown()
            return exited.await(timeout, unit)
        }
        override fun waitFor(): Int = exited.await().let { 0 }
        override fun exitValue(): Int = if (exited.count == 0L) 0 else throw IllegalThreadStateException()
        override fun destroy() = destroyForcibly().let { }
        override fun destroyForcibly(): Process = also { killed = true; exited.countDown() }
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
    }
}
