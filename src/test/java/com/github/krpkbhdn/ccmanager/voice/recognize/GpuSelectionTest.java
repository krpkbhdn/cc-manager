package com.github.krpkbhdn.ccmanager.voice.recognize;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuSelectionTest {

    private static final GgmlGpu INTEGRATED = new GgmlGpu(0, "Vulkan0", "Intel(R) Arc(TM) Graphics", true);
    private static final GgmlGpu NVIDIA = new GgmlGpu(1, "CUDA0", "NVIDIA GeForce RTX 5080", false);

    @Test
    void cpuUsesNoGpu() {
        assertThat(GpuSelection.ordinalFor(ComputeDevice.CPU, List.of(NVIDIA))).isEmpty();
    }

    @Test
    void matchesByNameNotByProbeIndex() {
        var device = ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080");

        assertThat(GpuSelection.ordinalFor(device, List.of(INTEGRATED, NVIDIA))).hasValue(1);
    }

    @Test
    void fallsBackToTheProbeIndexWhenNamesDiffer() {
        var device = ComputeDevice.nvidia(1, "RTX");

        assertThat(GpuSelection.ordinalFor(device, List.of(INTEGRATED, new GgmlGpu(1, "CUDA0", "Other", false)))).hasValue(1);
    }

    @Test
    void blankLabelNeverMatchesByName() {
        var device = ComputeDevice.nvidia(1, " ");

        assertThat(GpuSelection.ordinalFor(device, List.of(INTEGRATED, NVIDIA))).hasValue(1);
        assertThat(GpuSelection.ordinalFor(ComputeDevice.nvidia(5, ""), List.of(INTEGRATED, NVIDIA))).isEmpty();
    }

    @Test
    void identicalGpusAreToldApartByTheProbeIndex() {
        var first = new GgmlGpu(0, "CUDA0", "NVIDIA GeForce RTX 5080", false);
        var second = new GgmlGpu(1, "CUDA1", "NVIDIA GeForce RTX 5080", false);

        assertThat(GpuSelection.ordinalFor(ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080"), List.of(first, second))).hasValue(0);
        assertThat(GpuSelection.ordinalFor(ComputeDevice.nvidia(1, "NVIDIA GeForce RTX 5080"), List.of(first, second))).hasValue(1);
    }

    @Test
    void gpuMissingFromTheRuntimeIsEmpty() {
        assertThat(GpuSelection.ordinalFor(ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080"), List.of())).isEmpty();
    }
}
