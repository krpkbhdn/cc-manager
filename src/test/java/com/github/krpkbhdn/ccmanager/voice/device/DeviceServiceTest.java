package com.github.krpkbhdn.ccmanager.voice.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceServiceTest {

    private static final ComputeDevice RTX = ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 4090");
    private final DeviceService withGpu = new DeviceService(List.of(() -> List.of(RTX)));
    private final DeviceService cpuOnly = new DeviceService(List.of());

    @Test
    void cpuComesFirst() {
        assertThat(withGpu.devices()).containsExactly(ComputeDevice.CPU, RTX);
    }

    @Test
    void refreshReprobes() {
        var gpus = new ArrayList<ComputeDevice>();
        var service = new DeviceService(List.of(() -> List.copyOf(gpus)));
        assertThat(service.devices()).hasSize(1);

        gpus.add(RTX);

        assertThat(service.devices()).hasSize(1);
        assertThat(service.refresh()).hasSize(2);
    }

    @Test
    void selectsCpu() {
        assertThat(withGpu.select("CPU")).isEqualTo(ComputeDevice.CPU);
    }

    @Test
    void gpuAndBestPickFirstGpu() {
        assertThat(withGpu.select("gpu")).isEqualTo(RTX);
        assertThat(withGpu.select("best")).isEqualTo(RTX);
    }

    @Test
    void gpuAndBestFallBackToCpu() {
        assertThat(cpuOnly.select("gpu")).isEqualTo(ComputeDevice.CPU);
        assertThat(cpuOnly.select("best")).isEqualTo(ComputeDevice.CPU);
    }

    @Test
    void selectsOneOfTwoIdenticalGpusById() {
        var second = ComputeDevice.nvidia(1, RTX.label());
        var twin = new DeviceService(List.of(() -> List.of(RTX, second)));

        assertThat(twin.select("gpu1")).isEqualTo(second);
        assertThat(twin.select("GPU0")).isEqualTo(RTX);
        assertThat(twin.select(RTX.label())).isEqualTo(RTX);
    }

    @Test
    void selectsByPartOfNameIgnoringCase() {
        assertThat(withGpu.select("rtx 4090")).isEqualTo(RTX);
    }

    @Test
    void unknownDeviceIsRejected() {
        assertThatThrownBy(() -> withGpu.select("radeon")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("radeon");
    }

    @Test
    void buildMatchesDeviceFlavor() {
        var all = List.of(RuntimeBuild.WINDOWS_X64_CPU, RuntimeBuild.WINDOWS_X64_CUDA12);

        assertThat(withGpu.buildFor(RTX, all)).isEqualTo(RuntimeBuild.WINDOWS_X64_CUDA12);
        assertThat(withGpu.buildFor(ComputeDevice.CPU, all)).isEqualTo(RuntimeBuild.WINDOWS_X64_CPU);
    }

    @Test
    void gpuWithoutCudaBuildGetsCpuBuild() {
        assertThat(withGpu.buildFor(RTX, List.of(RuntimeBuild.WINDOWS_X64_CPU))).isEqualTo(RuntimeBuild.WINDOWS_X64_CPU);
    }

    @Test
    void effectiveDeviceFallsBackToCpuWithoutCudaBuild() {
        var cpuBuilds = List.of(RuntimeBuild.WINDOWS_X64_CPU);
        var allBuilds = List.of(RuntimeBuild.WINDOWS_X64_CPU, RuntimeBuild.WINDOWS_X64_CUDA12);

        assertThat(withGpu.effectiveDevice(RTX, cpuBuilds)).isEqualTo(ComputeDevice.CPU);
        assertThat(withGpu.effectiveDevice(RTX, allBuilds)).isEqualTo(RTX);
    }

    @Test
    void noBuildsIsAnError() {
        assertThatThrownBy(() -> withGpu.buildFor(RTX, List.of())).isInstanceOf(IllegalStateException.class);
    }
}
