package com.github.krpkbhdn.ccmanager.voice.recognize;

import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Predicate;

/** Finds the ggml GPU a {@link ComputeDevice} stands for; the probe's index (CUDA driver) need not equal ggml's ordinal. */
final class GpuSelection {

    private GpuSelection() {}

    /**
     * Empty for the CPU, or for a GPU the loaded backends do not expose (no driver, CPU build). Among several
     * GPUs of the same name the device's {@code gpuIndex}-th one (by ggml order) wins; with no name match the
     * ggml ordinal equal to {@code gpuIndex} is used.
     */
    static OptionalInt ordinalFor(ComputeDevice device, List<GgmlGpu> gpus) {
        if (device.kind() != ComputeDevice.Kind.GPU) {
            return OptionalInt.empty();
        }
        var label = device.label().strip().toLowerCase(Locale.ROOT);
        var sameName = label.isEmpty() ? List.<GgmlGpu>of() : gpus.stream().filter(named(label)).toList();
        if (!sameName.isEmpty()) {
            int pick = sameName.size() > device.gpuIndex() && device.gpuIndex() >= 0 ? device.gpuIndex() : 0;
            return OptionalInt.of(sameName.get(pick).ordinal());
        }
        return gpus.stream().mapToInt(GgmlGpu::ordinal).filter(ordinal -> ordinal == device.gpuIndex()).findFirst();
    }

    private static Predicate<GgmlGpu> named(String label) {
        return gpu -> gpu.name().toLowerCase(Locale.ROOT).contains(label)
                || gpu.description().toLowerCase(Locale.ROOT).contains(label);
    }
}
