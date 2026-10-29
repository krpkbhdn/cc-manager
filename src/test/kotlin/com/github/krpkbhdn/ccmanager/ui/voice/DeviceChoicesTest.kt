package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeviceChoicesTest {
    private val rtx = ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080")

    @Test
    fun `a single GPU is chosen by its name`() {
        val choices = deviceChoicesOf(listOf(ComputeDevice.CPU, rtx))

        assertThat(choices).containsExactly(DeviceChoice("best", "Best available (auto)"), DeviceChoice("cpu", "CPU"), DeviceChoice(rtx.label(), rtx.label()))
    }

    @Test
    fun `identical GPUs get a numbered label and are chosen by id`() {
        val second = ComputeDevice.nvidia(1, rtx.label())

        val choices = deviceChoicesOf(listOf(ComputeDevice.CPU, rtx, second)).drop(2)

        assertThat(choices).containsExactly(DeviceChoice("gpu0", "NVIDIA GeForce RTX 5080 (GPU 0)"), DeviceChoice("gpu1", "NVIDIA GeForce RTX 5080 (GPU 1)"))
    }

    @Test
    fun `without GPUs only auto and the CPU are offered`() {
        assertThat(deviceChoicesOf(emptyList()).map { it.spec }).containsExactly("best", "cpu")
    }

    @Test
    fun `the label follows the resolved spec and a vanished device says Best is used`() {
        val choices = deviceChoicesOf(listOf(ComputeDevice.CPU, rtx))

        assertThat(deviceLabelOf(choices, "cpu", "cpu", notFound = false)).isEqualTo("CPU")
        assertThat(deviceLabelOf(choices, "best", "Gone", notFound = true)).isEqualTo("Gone (not found, using Best)")
        assertThat(deviceLabelOf(choices, "gpu", "gpu", notFound = false)).isEqualTo("gpu")
    }
}
