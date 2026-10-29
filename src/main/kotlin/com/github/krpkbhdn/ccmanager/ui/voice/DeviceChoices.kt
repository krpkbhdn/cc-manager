package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice

const val BEST_DEVICE = "best"
private const val CPU_DEVICE = "cpu"
private const val BEST_LABEL = "Best available (auto)"
private const val NOT_FOUND_SUFFIX = "(not found, using Best)"

/** Auto, the CPU and every GPU; a GPU is chosen by its name unless another one has the same name, then by its id and a numbered label. */
fun deviceChoicesOf(found: List<ComputeDevice>): List<DeviceChoice> {
    val gpus = found.filter { it.kind() == ComputeDevice.Kind.GPU }
    val names = gpus.groupingBy { it.label() }.eachCount()
    return listOf(DeviceChoice(BEST_DEVICE, BEST_LABEL), DeviceChoice(CPU_DEVICE, "CPU")) + gpus.map { gpu ->
        if (names.getValue(gpu.label()) > 1) DeviceChoice(gpu.id(), "${gpu.label()} (GPU ${gpu.gpuIndex()})") else DeviceChoice(gpu.label(), gpu.label())
    }
}

/** The label of the saved device spec; a vanished device says that Best is used instead. */
fun deviceLabelOf(choices: List<DeviceChoice>, resolvedSpec: String, savedSpec: String, notFound: Boolean): String =
    if (notFound) "$savedSpec $NOT_FOUND_SUFFIX" else choices.firstOrNull { it.spec == resolvedSpec }?.label ?: resolvedSpec
