package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.preset.BundledPresetLoader
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService
import com.github.krpkbhdn.ccmanager.ui.presets.PresetPayloads
import com.github.krpkbhdn.ccmanager.ui.presets.parseSettings
import java.nio.file.Files
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate

/** The newest preview service, so shots can fill it with the presets they need. */
internal object PreviewPresets {
    @Volatile
    lateinit var current: ProjectPresetService
}

/** A real [ProjectPresetService] on a throwaway SQLite file with the bundled General preset seeded like the app does. */
fun previewPresetService(): ProjectPresetService {
    val file = Files.createTempFile("ccm-preview-presets", ".db").also { it.toFile().deleteOnExit() }
    val dataSource = DriverManagerDataSource("jdbc:sqlite:$file?journal_mode=WAL&busy_timeout=5000")
    Flyway.configure().dataSource(dataSource).load().migrate()
    return ProjectPresetService(JdbcClient.create(dataSource), TransactionTemplate(DataSourceTransactionManager(dataSource)), BundledPresetLoader()).also {
        it.syncBundled()
        PreviewPresets.current = it
    }
}

private val themes = listOf("Spring Boot service", "Compose desktop app", "Angular frontend", "Python data job", "Terraform module", "Kotlin library", "Docs site", "Go microservice")

/** Adds [count] presets with a varied number of components so the list and its row summaries look like a busy user's. */
fun ProjectPresetService.seedMany(count: Int) {
    repeat(count) { index ->
        val preset = create("${themes[index % themes.size]} ${index / themes.size + 1}", if (index % 3 == 0) "" else "Setup for ${themes[index % themes.size].lowercase()} projects.")
        repeat(index % 5) { n -> addComponent(preset.id(), PresetComponentKind.INSTRUCTION, "Rule ${n + 1}", PresetPayloads.instruction("Rule ${n + 1}: keep changes small and tested.")) }
        if (index % 2 == 0) addComponent(preset.id(), PresetComponentKind.SKILL, "review-${index}", PresetPayloads.skill(mapOf("SKILL.md" to "---\nname: review-$index\ndescription: \"Reviews a diff.\"\n---\n\nReview it.\n")))
        if (index % 4 == 1) addComponent(preset.id(), PresetComponentKind.FILE, ".editorconfig", PresetPayloads.file(".editorconfig", "root = true\n", emptyList()))
    }
}

/** One preset with 10 plugins, 6 skills, 12 instructions, 3 files, an MCP server, an agent and settings. */
fun ProjectPresetService.seedHeavy(): ProjectPreset {
    val preset = create("Full-stack service platform with a deliberately long name", "Everything a new service in the platform needs: tooling, rules, skills, files and guard rails.")
    val id = preset.id()
    addComponent(id, PresetComponentKind.MARKETPLACE, "claude-plugins-official", PresetPayloads.marketplace("anthropics/claude-plugins-official"))
    addComponent(id, PresetComponentKind.MARKETPLACE, "acme-internal", PresetPayloads.marketplace("https://git.acme.dev/platform/claude-plugins.git"))
    listOf("frontend-design", "context7", "ponytail", "code-review", "terraform", "postgres-tools", "github", "playwright", "security-guidance", "commit-commands")
        .forEachIndexed { index, name -> addComponent(id, PresetComponentKind.PLUGIN, "$name@${if (index % 3 == 2) "acme-internal" else "claude-plugins-official"}", PresetPayloads.plugin()) }
    listOf("code-review", "commit-message", "sql-migration-review", "changelog-writer", "release-notes", "dry-refactoring").forEach { name ->
        addComponent(id, PresetComponentKind.SKILL, name, PresetPayloads.skill(mapOf("SKILL.md" to "---\nname: $name\ndescription: \"Use when the user asks for $name; follows the platform's conventions.\"\n---\n\n# $name\n\nSteps.\n", "reference.md" to "More.\n")))
    }
    listOf("Duplicate code", "Clean code", "Comments", "Conventions", "Testing", "Error handling", "Logging", "Security", "Database migrations", "API design", "Git workflow", "Releases").forEach { title ->
        addComponent(id, PresetComponentKind.INSTRUCTION, title, PresetPayloads.instruction("## $title\n\n- Follow the platform guide for ${title.lowercase()}.\n- Ask before breaking it.\n"))
    }
    addComponent(id, PresetComponentKind.FILE, ".jscpd.json", PresetPayloads.file(".jscpd.json", "{\n  \"threshold\": 3\n}\n", listOf("jscpd-report/")))
    addComponent(id, PresetComponentKind.FILE, ".editorconfig", PresetPayloads.file(".editorconfig", "root = true\n\n[*]\nindent_style = space\n", emptyList()))
    addComponent(id, PresetComponentKind.FILE, "docs/ARCHITECTURE.md", PresetPayloads.file("docs/ARCHITECTURE.md", "# Architecture\n", emptyList()))
    addComponent(id, PresetComponentKind.MCP_SERVER, "github", PresetPayloads.mcp("{\"type\":\"stdio\",\"command\":\"npx\",\"args\":[\"-y\",\"@modelcontextprotocol/server-github\"]}"))
    addComponent(id, PresetComponentKind.MCP_SERVER, "docs", PresetPayloads.mcp("{\"type\":\"http\",\"url\":\"https://mcp.acme.dev/docs\"}"))
    addComponent(id, PresetComponentKind.AGENT, "reviewer", PresetPayloads.agent("---\nname: reviewer\ndescription: Reviews a diff for missing tests and unclear names.\ntools: Read, Grep, Glob\n---\n\nYou review code changes.\n"))
    addComponent(id, PresetComponentKind.SETTINGS, "settings", PresetPayloads.settings(parseSettings("{\"permissions\":{\"deny\":[\"Read(./.env)\"]},\"env\":{\"CLAUDE_CODE_DISABLE_TELEMETRY\":\"1\"}}")))
    return get(id).orElseThrow()
}
