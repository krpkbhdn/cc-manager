package com.github.krpkbhdn.ccmanager.ui.presets

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.name

/** Caps for copying a skill folder into a preset, which lives in the app database. */
data class SkillImportLimits(val maxFiles: Int = 40, val maxFileBytes: Long = 100_000, val maxTotalBytes: Long = 400_000)

/** The text files of a skill folder by relative path, what was left out and why, and [problem] when it cannot be a skill. */
data class SkillImport(val name: String, val files: Map<String, String>, val skipped: List<String>, val problem: String?) {
    /** "3 files · 4 KB", sizes by characters of text. */
    fun summary(): String {
        val kilobytes = maxOf(1, files.values.sumOf { it.length } / BYTES_PER_KB)
        return "${files.size} ${if (files.size == 1) "file" else "files"}$SEPARATOR$kilobytes KB"
    }
}

private const val BYTES_PER_KB = 1000

/** Reads the text files of [directory] (no links, no hidden folders) within [limits]; SKILL.md must be among them. */
fun readSkillFolder(directory: Path, limits: SkillImportLimits = SkillImportLimits()): SkillImport {
    val name = directory.name
    val files = linkedMapOf<String, String>()
    val skipped = mutableListOf<String>()
    var total = 0L
    try {
        Files.walk(directory).use { stream ->
            stream.filter { it != directory }.sorted().forEach { path ->
                val relative = directory.relativize(path).joinToString("/")
                when {
                    Files.isSymbolicLink(path) -> skipped += "$relative (link)"
                    hiddenFolder(directory, path) -> Unit
                    !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> Unit
                    files.size >= limits.maxFiles -> skipped += "$relative (more than ${limits.maxFiles} files)"
                    Files.size(path) > limits.maxFileBytes -> skipped += "$relative (too large)"
                    total + Files.size(path) > limits.maxTotalBytes -> skipped += "$relative (over the total size limit)"
                    else -> decodeText(Files.readAllBytes(path))?.let { files[relative] = it; total += Files.size(path) } ?: run { skipped += "$relative (not text)" }
                }
            }
        }
    } catch (e: IOException) {
        return SkillImport(name, emptyMap(), skipped, "Could not read the folder: ${e.message}")
    }
    val problem = folderNameProblem(name) ?: if (SKILL_FILE in files) null else "The folder has no $SKILL_FILE"
    return SkillImport(name, files, skipped, problem)
}

private fun hiddenFolder(root: Path, path: Path): Boolean = root.relativize(path).toList().dropLast(1).any { it.name.startsWith(".") }

/** The bytes as UTF-8 text, or null for binary content (a NUL byte or invalid UTF-8). */
fun decodeText(bytes: ByteArray): String? {
    if (bytes.any { it == 0.toByte() }) return null
    return try {
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }
}
