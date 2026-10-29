package com.github.krpkbhdn.ccmanager.voice.device;

import java.util.List;

/** One way of finding GPUs; returns an empty list when none are found or the tool is missing. */
public interface GpuProbe {

    List<ComputeDevice> probe();
}
