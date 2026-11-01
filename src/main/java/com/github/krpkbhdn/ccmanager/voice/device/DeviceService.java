package com.github.krpkbhdn.ccmanager.voice.device;

import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The CPU plus every GPU the probes find, cached until {@link #refresh()}. */
@Component
public class DeviceService {

    private final List<GpuProbe> probes;
    private volatile List<ComputeDevice> cached;

    public DeviceService(List<GpuProbe> probes) {
        this.probes = List.copyOf(probes);
    }

    public List<ComputeDevice> devices() {
        List<ComputeDevice> devices = cached;
        return devices != null ? devices : refresh();
    }

    public List<ComputeDevice> refresh() {
        List<ComputeDevice> found = Stream.concat(Stream.of(ComputeDevice.CPU),
                probes.stream().flatMap(probe -> probe.probe().stream())).toList();
        cached = found;
        return found;
    }

    /** "cpu", "gpu" (first GPU, falls back to the CPU when there is none), "best", a device id such as "gpu1", else a case-insensitive part of a device name. */
    public ComputeDevice select(String spec) {
        String wanted = spec.strip().toLowerCase(Locale.ROOT);
        return switch (wanted) {
            case "cpu" -> ComputeDevice.CPU;
            case "gpu", "best" -> firstGpuOrCpu();
            default -> devices().stream().filter(device -> device.id().equals(wanted)).findFirst()
                    .or(() -> devices().stream().filter(device -> device.label().toLowerCase(Locale.ROOT).contains(wanted)).findFirst()).orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + spec + ", available: " + devices().stream().map(ComputeDevice::label).toList()));
        };
    }

    public RuntimeBuild buildFor(ComputeDevice device) {
        return buildFor(device, RuntimeBuild.forCurrentPlatform());
    }

    RuntimeBuild buildFor(ComputeDevice device, List<RuntimeBuild> builds) {
        return builds.stream().filter(build -> build.flavor() == device.flavor()).findFirst()
                .or(() -> builds.stream().filter(build -> build.flavor() == RuntimeBuild.Flavor.CPU).findFirst())
                .orElseThrow(() -> new IllegalStateException("No whisper runtime build for this platform"));
    }

    /** The device that really runs: the CPU when {@link #buildFor} had to fall back to the CPU build. */
    public ComputeDevice effectiveDevice(ComputeDevice device) {
        return effectiveDevice(device, RuntimeBuild.forCurrentPlatform());
    }

    ComputeDevice effectiveDevice(ComputeDevice device, List<RuntimeBuild> builds) {
        return buildFor(device, builds).flavor() == device.flavor() ? device : ComputeDevice.CPU;
    }

    private ComputeDevice firstGpuOrCpu() {
        return devices().stream().filter(device -> device.kind() == ComputeDevice.Kind.GPU).findFirst()
                .orElse(ComputeDevice.CPU);
    }
}
