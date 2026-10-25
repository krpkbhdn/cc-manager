package com.github.krpkbhdn.ccmanager.ui

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private val icoSizes = listOf(16, 24, 32, 48, 64, 128, 256)
private val icnsTypes = mapOf("ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024)
private const val LINUX_SIZE = 512
private const val ICO_HEADER_BYTES = 6
private const val ICO_ENTRY_BYTES = 16
private const val ICNS_HEADER_BYTES = 8

private fun png(size: Int): ByteArray = ByteArrayOutputStream().also { ImageIO.write(drawAppIcon(size), "png", it) }.toByteArray()

private fun ico(): ByteArray {
    val images = icoSizes.map { it to png(it) }
    val buffer = ByteBuffer.allocate(ICO_HEADER_BYTES + ICO_ENTRY_BYTES * images.size + images.sumOf { it.second.size }).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(0).putShort(1).putShort(images.size.toShort())
    var offset = ICO_HEADER_BYTES + ICO_ENTRY_BYTES * images.size
    images.forEach { (size, data) ->
        buffer.put((size % 256).toByte()).put((size % 256).toByte()).put(0).put(0).putShort(1).putShort(32).putInt(data.size).putInt(offset)
        offset += data.size
    }
    images.forEach { buffer.put(it.second) }
    return buffer.array()
}

private fun icns(): ByteArray {
    val images = icnsTypes.map { (type, size) -> type to png(size) }
    val buffer = ByteBuffer.allocate(ICNS_HEADER_BYTES + images.sumOf { ICNS_HEADER_BYTES + it.second.size })
    buffer.put("icns".toByteArray()).putInt(buffer.capacity())
    images.forEach { (type, data) -> buffer.put(type.toByteArray()).putInt(ICNS_HEADER_BYTES + data.size).put(data) }
    return buffer.array()
}

/** Renders the code-drawn app icon into the installer icon files (args[0] = target directory, default packaging). */
fun main(args: Array<String>) {
    val dir = Path.of(args.firstOrNull() ?: "packaging")
    Files.createDirectories(dir)
    Files.write(dir.resolve("icon.ico"), ico())
    Files.write(dir.resolve("icon.icns"), icns())
    Files.write(dir.resolve("icon.png"), png(LINUX_SIZE))
}
