package com.github.krpkbhdn.ccmanager.ui.presets

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SkillImportTest {
    @TempDir
    lateinit var root: Path

    private fun skill(name: String = "review"): Path = Files.createDirectory(root.resolve(name)).also { Files.writeString(it.resolve(SKILL_FILE), "---\nname: $name\n---\n") }

    @Test
    fun `reads the text files of a skill folder by relative path`() {
        val dir = skill()
        Files.createDirectories(dir.resolve("refs"))
        Files.writeString(dir.resolve("refs/a.md"), "ref")
        val read = readSkillFolder(dir)
        assertThat(read.problem).isNull()
        assertThat(read.name).isEqualTo("review")
        assertThat(read.files.keys).containsExactlyInAnyOrder(SKILL_FILE, "refs/a.md")
        assertThat(read.summary()).isEqualTo("2 files · 1 KB")
    }

    @Test
    fun `binary files hidden folders and oversized files are skipped`() {
        val dir = skill()
        Files.write(dir.resolve("logo.png"), byteArrayOf(1, 0, 2))
        Files.createDirectories(dir.resolve(".git"))
        Files.writeString(dir.resolve(".git/config"), "x")
        Files.writeString(dir.resolve("big.md"), "x".repeat(50))
        val read = readSkillFolder(dir, SkillImportLimits(maxFiles = 10, maxFileBytes = 40, maxTotalBytes = 1000))
        assertThat(read.files.keys).containsExactly(SKILL_FILE)
        assertThat(read.skipped).containsExactlyInAnyOrder("big.md (too large)", "logo.png (not text)")
    }

    @Test
    fun `stops at the file count and the total size`() {
        val dir = skill()
        Files.writeString(dir.resolve("a.md"), "a")
        Files.writeString(dir.resolve("b.md"), "b")
        val byCount = readSkillFolder(dir, SkillImportLimits(maxFiles = 2, maxFileBytes = 100, maxTotalBytes = 1000))
        assertThat(byCount.files).hasSize(2)
        assertThat(byCount.skipped).hasSize(1)
        val byTotal = readSkillFolder(dir, SkillImportLimits(maxFiles = 10, maxFileBytes = 100, maxTotalBytes = 20))
        assertThat(byTotal.skipped).anyMatch { it.endsWith("(over the total size limit)") }
    }

    @Test
    fun `a folder without SKILL md or with a bad name is not a skill`() {
        val empty = Files.createDirectory(root.resolve("empty"))
        assertThat(readSkillFolder(empty).problem).contains("no SKILL.md")
        val spaced = skill("my skill")
        assertThat(readSkillFolder(spaced).problem).contains("letters, digits")
    }

    @Test
    fun `invalid utf8 counts as binary`() {
        assertThat(decodeText(byteArrayOf(0x68, 0x69))).isEqualTo("hi")
        assertThat(decodeText(byteArrayOf(0xC3.toByte(), 0x28))).isNull()
    }
}
