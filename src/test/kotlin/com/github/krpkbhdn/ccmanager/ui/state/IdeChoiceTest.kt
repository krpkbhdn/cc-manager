package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.workspace.InstalledIde
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class IdeChoiceTest {
    private val idea = InstalledIde("idea", "IntelliJ IDEA", listOf("idea"))
    private val code = InstalledIde("code", "VS Code", listOf("code"))
    private val noActions = IdeMenuActions({}, {}, {}, {})
    private val home = Path.of("/home/me")

    @Test
    fun `remembered IDE wins when installed`() {
        assertThat(resolveDefaultIde(listOf(idea, code), "code")).isEqualTo(code)
    }

    @Test
    fun `falls back to the first IDE when the remembered one is gone`() {
        assertThat(resolveDefaultIde(listOf(idea, code), "zed")).isEqualTo(idea)
        assertThat(resolveDefaultIde(listOf(idea, code), null)).isEqualTo(idea)
    }

    @Test
    fun `no IDE means no default`() {
        assertThat(resolveDefaultIde(emptyList(), "idea")).isNull()
    }

    @Test
    fun `menu rows are neutral and followed by add and check again`() {
        val entries = ideMenuEntries(listOf(idea, code), idea, noActions) { {} }

        assertThat(entries.map { it.label }).containsExactly("IntelliJ IDEA", "VS Code", "Add IDE...", "Check again")
        assertThat(entries.take(2)).allSatisfy { assertThat(it.checked).isNull(); assertThat(it.leading).isNotNull(); assertThat(it.trailing).isNotNull() }
        assertThat(entries[2].dividerAbove).isTrue()
    }

    @Test
    fun `menu without IDEs still offers adding and checking again`() {
        assertThat(ideMenuEntries(emptyList(), null, noActions) { {} }.map { it.label }).containsExactly("Add IDE...", "Check again")
    }

    @Test
    fun `selecting a row opens that IDE`() {
        val opened = mutableListOf<String>()
        val entries = ideMenuEntries(listOf(idea, code), idea, IdeMenuActions(opened::add, {}, {}, {})) { {} }

        entries[1].onSelect()

        assertThat(opened).containsExactly("code")
    }

    @Test
    fun `windows uses the exe but not script shims`() {
        assertThat(ideIconCandidates(listOf("C:/Idea/bin/idea64.exe"), "Windows 11", home)).containsExactly(Path.of("C:/Idea/bin/idea64.exe"))
    }

    @Test
    fun `windows shim of VS Code resolves to the executable in the install root`(@TempDir root: Path) {
        val shim = Files.createDirectories(root.resolve("Microsoft VS Code/bin")).resolve("code.cmd")

        val candidates = ideIconCandidates(listOf(shim.toString()), "Windows 11", home)

        assertThat(candidates).contains(root.resolve("Microsoft VS Code/Code.exe"))
    }

    @Test
    fun `windows shim of Insiders resolves to its own executable name`(@TempDir root: Path) {
        val shim = Files.createDirectories(root.resolve("Code/bin")).resolve("code-insiders.cmd")

        assertThat(ideIconCandidates(listOf(shim.toString()), "Windows 11", home)).contains(root.resolve("Code/Code - Insiders.exe"))
    }

    @Test
    fun `toolbox script gives the executable it launches`(@TempDir root: Path) {
        val script = Files.writeString(root.resolve("idea.cmd"), "@echo off\n\"C:/Apps/IDEA/bin/idea64.exe\" %*\n")

        assertThat(ideIconCandidates(listOf(script.toString()), "Windows 11", home)).startsWith(Path.of("C:/Apps/IDEA/bin/idea64.exe"))
    }

    @Test
    fun `a script without a quoted executable adds only sibling guesses`(@TempDir root: Path) {
        val script = Files.writeString(root.resolve("tool.cmd"), "@echo off\ncall other.bat\n")

        assertThat(ideIconCandidates(listOf(script.toString()), "Windows 11", home)).allMatch { it.fileName.toString() == "Tool.exe" }
    }

    @Test
    fun `mac looks for the app bundle in both applications folders`() {
        assertThat(ideIconCandidates(listOf("open", "-a", "IntelliJ IDEA"), "Mac OS X", home))
            .containsExactly(Path.of("/Applications", "IntelliJ IDEA.app"), home.resolve("Applications").resolve("IntelliJ IDEA.app"))
    }

    @Test
    fun `linux gives no icon source`() {
        assertThat(ideIconCandidates(listOf("/usr/bin/idea"), "Linux", home)).isEmpty()
    }
}
