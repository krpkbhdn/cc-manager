package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService
import com.github.krpkbhdn.ccmanager.extension.skill.Skill
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment
import java.nio.file.Path
import org.mockito.Mockito.mock

private const val WRITE_DELAY_MILLIS = 500L
private val userRoot = Path.of("C:/Users/dev/.claude/skills")

private val reviewBody = """
    # Code review

    Review the staged diff the way a careful teammate would. Read the whole change before commenting.

    ## What to check

    - Behaviour changes that no test covers
    - Error paths that swallow exceptions
    - Names that hide intent, such as `process()` or `handleData()`
    - Public methods added without a caller

    ## How to report

    1. Start with a one-line verdict: ship, fix first, or rethink.
    2. List findings by severity, each with file and line.
    3. Suggest the smallest fix, not a rewrite.

    ```text
    verdict: fix first
    src/main/java/OrderService.java:88  error is logged and dropped
    ```

    Never run the test suite unless asked; say which tests you would run instead.
""".trimIndent()

private val migrationBody = """
    # SQL migration review

    Use before merging any file under `db/migration`.

    ## Rules

    - Applied migrations are never edited; add a new `V<n>__name.sql`.
    - Every new column on a large table is nullable or has a default.
    - Indexes are created `CONCURRENTLY` on PostgreSQL.

    ```sql
    ALTER TABLE orders ADD COLUMN archived_at timestamptz;
    CREATE INDEX CONCURRENTLY idx_orders_archived ON orders (archived_at);
    ```
""".trimIndent()

private fun starterBody(name: String) = "# $name\n\nDescribe what this skill does and when Claude should use it.\n"

private fun fileOf(root: Path, name: String) = root.resolve(name).resolve("SKILL.md")

/** Skills for the preview: user and project skills plus read-only skills of plugins, with real-looking SKILL.md bodies. */
class FakeSkills : SkillService(mock(HostEnvironment::class.java), mock(PluginService::class.java)) {
    private val projectRoot = Path.of("C:/Users/dev/Work/cc-manager/.claude/skills")
    private val bodies = mutableMapOf<Path, String>()
    private var all = mutableListOf(
        skill("code-review", "Review the staged diff for missing tests, swallowed errors and unclear names. Use when the user asks for a review.", ExtensionScope.USER, "", fileOf(userRoot, "code-review"), reviewBody),
        skill("commit-message", "Write a one-line commit message in the project's format from the staged changes.", ExtensionScope.USER, "", fileOf(userRoot, "commit-message")),
        skill("sql-migration-review", "Check a Flyway migration for locking, defaults and indexes before it is merged.", ExtensionScope.USER, "", fileOf(userRoot, "sql-migration-review"), migrationBody),
        skill("changelog-writer", "Turn merged pull requests into a customer-facing changelog entry.", ExtensionScope.USER, "", fileOf(userRoot, "changelog-writer")),
        skill("release-notes", "Draft release notes from the commits since the last tag.", ExtensionScope.USER, "", fileOf(userRoot, "release-notes")),
        skill("compose-preview", "Render a Compose screen offscreen and describe what is wrong with the layout.", ExtensionScope.PROJECT, "", fileOf(projectRoot, "compose-preview")),
        skill("frontend-design", "Distinctive, production-grade interfaces that avoid generic AI aesthetics.", ExtensionScope.USER, "frontend-design@claude-plugins-official", Path.of("C:/Users/dev/.claude/plugins/cache/frontend-design/skills/frontend-design/SKILL.md")),
        skill("systematic-debugging", "Find the root cause of a failing test before changing any code.", ExtensionScope.USER, "superpowers@superpowers-marketplace", Path.of("C:/Users/dev/.claude/plugins/cache/superpowers/skills/systematic-debugging/SKILL.md")),
        skill("brainstorming", "Explore intent and requirements before any creative or feature work.", ExtensionScope.USER, "superpowers@superpowers-marketplace", Path.of("C:/Users/dev/.claude/plugins/cache/superpowers/skills/brainstorming/SKILL.md")),
        skill("ponytail", "Lazy senior developer mode: the smallest change that fully solves the task.", ExtensionScope.USER, "ponytail@claude-plugins-official", Path.of("C:/Users/dev/.claude/plugins/cache/ponytail/skills/ponytail/SKILL.md")),
    )

    override fun list(projectDirectory: Path?): List<Skill> = all.filter { projectDirectory != null || it.scope == ExtensionScope.USER || it.readOnly() }.toList()

    override fun read(skill: Skill): String = bodies[skill.file] ?: "---\nname: ${skill.name}\ndescription: ${skill.description}\n---\n\n${starterBody(skill.name)}"

    override fun create(scope: ExtensionScope, projectDirectory: Path?, name: String, description: String): Skill {
        Thread.sleep(WRITE_DELAY_MILLIS)
        val root = if (scope == ExtensionScope.USER) userRoot else projectRoot
        return skill(name, description, scope, "", fileOf(root, name), starterBody(name)).also { all += it }
    }

    override fun delete(skill: Skill) {
        Thread.sleep(WRITE_DELAY_MILLIS)
        all.removeIf { it.file == skill.file }
    }

    private fun skill(name: String, description: String, scope: ExtensionScope, pluginId: String, file: Path, body: String = starterBody(name)): Skill {
        bodies[file] = "---\nname: $name\ndescription: \"$description\"\n---\n\n$body"
        return Skill(name, description, scope, pluginId, file)
    }
}
