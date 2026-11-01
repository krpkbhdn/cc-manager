package com.github.krpkbhdn.ccmanager.voice.device;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Lists NVIDIA GPUs through the system CUDA driver library, without a child process. CUDA ordinals can differ
 * from PCI order; the recogniser matches devices by name (see GpuSelection).
 */
@Component
public class CudaDriverProbe implements GpuProbe {

    private static final int CUDA_SUCCESS = 0;
    private static final int NAME_BYTES = 256;

    /** The driver calls the probe needs; every failure is reported as a non-success or empty result. */
    interface Driver {
        boolean init();

        int deviceCount();

        Optional<String> deviceName(int ordinal);
    }

    private final Optional<Driver> driver;

    public CudaDriverProbe() {
        this(loadSystemDriver());
    }

    CudaDriverProbe(Optional<Driver> driver) {
        this.driver = driver;
    }

    @Override
    public List<ComputeDevice> probe() {
        return driver.filter(Driver::init)
                .map(found -> IntStream.range(0, found.deviceCount())
                        .mapToObj(ordinal -> found.deviceName(ordinal).map(name -> ComputeDevice.nvidia(ordinal, name)))
                        .flatMap(Optional::stream).toList())
                .orElse(List.of());
    }

    private static Optional<Driver> loadSystemDriver() {
        String library = System.getProperty("os.name").toLowerCase().contains("win") ? "nvcuda.dll" : "libcuda.so.1";
        try {
            // Arena.global(): the driver library stays loaded, whisper loads the same one later
            return Optional.of(new FfmDriver(SymbolLookup.libraryLookup(library, Arena.global())));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static final class FfmDriver implements Driver {

        private final MethodHandle init;
        private final MethodHandle getCount;
        private final MethodHandle getDevice;
        private final MethodHandle getName;

        FfmDriver(SymbolLookup lookup) {
            Linker linker = Linker.nativeLinker();
            var addr = ValueLayout.ADDRESS;
            var integer = ValueLayout.JAVA_INT;
            init = handle(linker, lookup, "cuInit", FunctionDescriptor.of(integer, integer));
            getCount = handle(linker, lookup, "cuDeviceGetCount", FunctionDescriptor.of(integer, addr));
            getDevice = handle(linker, lookup, "cuDeviceGet", FunctionDescriptor.of(integer, addr, integer));
            getName = handle(linker, lookup, "cuDeviceGetName", FunctionDescriptor.of(integer, addr, integer, integer));
        }

        @Override
        public boolean init() {
            try {
                return (int) init.invokeExact(0) == CUDA_SUCCESS;
            } catch (Throwable e) {
                return false;
            }
        }

        @Override
        public int deviceCount() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment count = arena.allocate(ValueLayout.JAVA_INT);
                return (int) getCount.invokeExact(count) == CUDA_SUCCESS ? count.get(ValueLayout.JAVA_INT, 0) : 0;
            } catch (Throwable e) {
                return 0;
            }
        }

        @Override
        public Optional<String> deviceName(int ordinal) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment device = arena.allocate(ValueLayout.JAVA_INT);
                if ((int) getDevice.invokeExact(device, ordinal) != CUDA_SUCCESS) {
                    return Optional.empty();
                }
                MemorySegment name = arena.allocate(NAME_BYTES);
                if ((int) getName.invokeExact(name, NAME_BYTES, device.get(ValueLayout.JAVA_INT, 0)) != CUDA_SUCCESS) {
                    return Optional.empty();
                }
                return Optional.of(name.getString(0).strip()).filter(text -> !text.isEmpty());
            } catch (Throwable e) {
                return Optional.empty();
            }
        }

        private static MethodHandle handle(Linker linker, SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
            return linker.downcallHandle(lookup.find(symbol).orElseThrow(() -> new IllegalStateException(symbol)), descriptor);
        }
    }
}
