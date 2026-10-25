package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.skill.Skill
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SkillsLogicTest {
    private val project = Path.of("C:/work/app")
    private val inProject = ExtensionContext.ForProject(1, "app", project)

    private fun skill(name: String, scope: ExtensionScope, description: String = "", pluginId: String = "") =
        Skill(name, description, scope, pluginId, Path.of("C:/skills/$name/SKILL.md"))

    @Test
    fun nameRuleMirrorsTheBackend() {
        assertThat(skillNameProblem("code-review", emptyList())).isNull()
        assertThat(skillNameProblem("a1-b2", emptyList())).isNull()
        assertThat(skillNameProblem("Code-Review", emptyList())).isNotNull()
        assertThat(skillNameProblem("code--review", emptyList())).isNotNull()
        assertThat(skillNameProblem("-code", emptyList())).isNotNull()
        assertThat(skillNameProblem("a".repeat(65), emptyList())).contains("64")
        assertThat(skillNameProblem("code-review", listOf("code-review"))).contains("already exists")
        assertThat(skillNameProblem(" ", emptyList())).isNotNull()
    }

    @Test
    fun descriptionMustNotBeBlank() {
        assertThat(skillDescriptionProblem(" \n ")).isNotNull()
        assertThat(skillDescriptionProblem("Use when reviewing")).isNull()
    }

    @Test
    fun newSkillsGoToUserOrProjectNeverLocal() {
        assertThat(newSkillScope(ExtensionContext.Global)).isEqualTo(ExtensionScope.USER)
        assertThat(newSkillScope(inProject)).isEqualTo(ExtensionScope.PROJECT)
    }

    @Test
    fun globalContextOwnsUserSkillsAndSeparatesPluginSkills() {
        val skills = listOf(skill("b", ExtensionScope.USER), skill("a", ExtensionScope.USER), skill("p", ExtensionScope.USER, pluginId = "x@m"))

        val groups = skillGroups(skills, ExtensionContext.Global)

        assertThat(groups.own.map { it.skill.name }).containsExactly("a", "b")
        assertThat(groups.inherited).isEmpty()
        assertThat(groups.plugins.map { it.skill.name }).containsExactly("p")
        assertThat(groups.plugins.single().removable).isFalse()
    }

    @Test
    fun projectContextInheritsUserSkillsReadOnly() {
        val groups = skillGroups(listOf(skill("u", ExtensionScope.USER), skill("p", ExtensionScope.PROJECT)), inProject)

        assertThat(groups.own.map { it.skill.name }).containsExactly("p")
        assertThat(groups.inherited.map { it.skill.name }).containsExactly("u")
        assertThat(groups.inherited.single().removable).isFalse()
        assertThat(groups.own.single().removable).isTrue()
    }

    @Test
    fun searchNeedsEveryWordInNameDescriptionOrPlugin() {
        val skills = listOf(
            skill("code-review", ExtensionScope.USER, "Reviews a diff"),
            skill("release-notes", ExtensionScope.USER, "Drafts notes from commits"),
            skill("debugging", ExtensionScope.USER, "Find root causes", pluginId = "superpowers@market"),
        )

        assertThat(skillGroups(skills, ExtensionContext.Global, "REVIEW diff").size).isEqualTo(1)
        assertThat(skillGroups(skills, ExtensionContext.Global, "notes review").size).isZero()
        assertThat(skillGroups(skills, ExtensionContext.Global, "superpowers").plugins).hasSize(1)
        assertThat(skillGroups(skills, ExtensionContext.Global, "  ").size).isEqualTo(3)
    }

    @Test
    fun bodyDropsFrontMatterOnly() {
        assertThat(skillBody("---\nname: a\ndescription: b\n---\n\n# A\n\ntext")).isEqualTo("# A\n\ntext")
        assertThat(skillBody("# No front matter\n")).isEqualTo("# No front matter")
        assertThat(skillBody("---\nname: a\nno end")).isEqualTo("---\nname: a\nno end")
    }

    @Test
    fun markdownBecomesHeadingsItemsParagraphsAndCode() {
        val blocks = markdownBlocks("# Title\n\nfirst line\nsecond line\n\n- one\n2. two\n\n```sql\nselect 1;\n\nselect 2;\n```\n\n## End")

        assertThat(blocks).containsExactly(
            MarkdownBlock.Heading(1, "Title"),
            MarkdownBlock.Paragraph("first line second line"),
            MarkdownBlock.Item("\u2022", "one"),
            MarkdownBlock.Item("2.", "two"),
            MarkdownBlock.Code("select 1;\n\nselect 2;"),
            MarkdownBlock.Heading(2, "End"),
        )
    }

    @Test
    fun unclosedCodeFenceStillShowsItsContent() {
        assertThat(markdownBlocks("```\nabc")).containsExactly(MarkdownBlock.Code("abc"))
    }
}
