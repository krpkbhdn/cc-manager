package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/** Lists the GPU devices of the ggml backends a runtime loaded. */
final class GgmlDevices {

    private static final int TYPE_GPU = 1;
    private static final int TYPE_INTEGRATED_GPU = 2;

    private GgmlDevices() {}

    static List<GgmlGpu> gpus(NativeRuntime runtime) {
        try {
            var count = (long) runtime.handle("ggml_backend_dev_count", FunctionDescriptor.of(JAVA_LONG)).invoke();
            var get = runtime.handle("ggml_backend_dev_get", FunctionDescriptor.of(ADDRESS, JAVA_LONG));
            var type = runtime.handle("ggml_backend_dev_type", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            var name = runtime.handle("ggml_backend_dev_name", FunctionDescriptor.of(ADDRESS, ADDRESS));
            var description = runtime.handle("ggml_backend_dev_description", FunctionDescriptor.of(ADDRESS, ADDRESS));
            var found = new ArrayList<GgmlGpu>();
            for (long i = 0; i < count; i++) {
                var device = (MemorySegment) get.invoke(i);
                int deviceType = (int) type.invoke(device);
                if (deviceType == TYPE_GPU || deviceType == TYPE_INTEGRATED_GPU) {
                    found.add(new GgmlGpu(found.size(), cString((MemorySegment) name.invoke(device)),
                            cString((MemorySegment) description.invoke(device)), deviceType == TYPE_INTEGRATED_GPU));
                }
            }
            return List.copyOf(found);
        } catch (Throwable e) {
            throw NativeFailure.of("Listing ggml devices", e);
        }
    }

    /** The C string at {@code pointer}, or "" for a NULL pointer (whisper returns NULL for an unknown id). */
    static String cString(MemorySegment pointer) {
        if (pointer.equals(MemorySegment.NULL)) {
            return "";
        }
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
