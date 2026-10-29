package com.github.krpkbhdn.ccmanager.voice.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CudaDriverProbeTest {

    private record FakeDriver(boolean initOk, List<Optional<String>> names) implements CudaDriverProbe.Driver {

        @Override
        public boolean init() {
            return initOk;
        }

        @Override
        public int deviceCount() {
            return names.size();
        }

        @Override
        public Optional<String> deviceName(int ordinal) {
            return names.get(ordinal);
        }
    }

    @Test
    void mapsEveryDeviceToItsOrdinalAndName() {
        var probe = new CudaDriverProbe(Optional.of(
                new FakeDriver(true, List.of(Optional.of("NVIDIA GeForce RTX 5080"), Optional.of("NVIDIA T4")))));

        assertThat(probe.probe()).containsExactly(
                ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080"), ComputeDevice.nvidia(1, "NVIDIA T4"));
    }

    @Test
    void deviceWithoutNameIsSkipped() {
        var probe = new CudaDriverProbe(Optional.of(new FakeDriver(true, List.of(Optional.empty(), Optional.of("Bar")))));

        assertThat(probe.probe()).containsExactly(ComputeDevice.nvidia(1, "Bar"));
    }

    @Test
    void failedInitGivesNoDevices() {
        assertThat(new CudaDriverProbe(Optional.of(new FakeDriver(false, List.of(Optional.of("Foo"))))).probe()).isEmpty();
    }

    @Test
    void missingDriverGivesNoDevices() {
        assertThat(new CudaDriverProbe(Optional.empty()).probe()).isEmpty();
    }

    @Test
    void realDriverProbeDoesNotThrow() {
        var devices = new CudaDriverProbe().probe();

        System.out.println("CUDA devices: " + devices);
        assertThat(devices).isNotNull();
    }
}
