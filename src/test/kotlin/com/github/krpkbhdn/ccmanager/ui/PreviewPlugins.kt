package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.extension.ClaudeCli
import com.github.krpkbhdn.ccmanager.extension.ExtensionException
import com.github.krpkbhdn.ccmanager.extension.ExtensionProperties
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginComponent
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginDetails
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService
import com.github.krpkbhdn.ccmanager.extension.plugin.ShownCommand
import java.nio.file.Path
import java.util.Random
import org.mockito.Mockito.mock

const val NEEDS_COMMAND_PLUGIN = "acme-deploy@acme-internal"
const val FAILING_PLUGIN = "legacy-linter@acme-internal"
private val previewProject = Path.of("C:/Users/dev/Work/cc-manager")
private val otherProject = Path.of("C:/Users/dev/Work/billing-api")
private const val FAKE_DELAY_MILLIS = 900L
private const val CATALOG_SIZE = 320

private val curated = listOf(
    Triple("frontend-design", "Distinctive, production-grade interfaces that avoid generic AI aesthetics.", 412_000L),
    Triple("context7", "Up-to-date library documentation pulled into the session on demand.", 268_000L),
    Triple("code-review", "Reviews a pull request with parallel agents and confidence-scored findings.", 187_000L),
    Triple("feature-dev", "Guided feature development with codebase exploration and architecture agents.", 143_500L),
    Triple("playwright", "Browser automation and end-to-end testing through Playwright MCP.", 121_300L),
    Triple("github", "Issues, pull requests and workflows from the session.", 96_800L),
    Triple("commit-commands", "Commit, push and open a pull request with one command.", 74_200L),
    Triple("security-guidance", "Warns about risky edits such as injection and unsafe deserialisation.", 33_900L),
    Triple("postgres-tools", "Inspect schemas and run read-only queries against PostgreSQL.", 18_400L),
    Triple("terraform", "Plan and review Terraform changes with state-aware guidance.", 9_730L),
    Triple("ponytail", "Lazy senior developer mode: the smallest change that fully solves the task.", 4_150L),
    Triple("superpowers", "A skill library for planning, debugging and test-driven work.", 61_000L),
    Triple("legacy-linter", "Runs the in-house linter rules before every commit.", 410L),
    Triple("acme-deploy", "Deploys the current branch to the Acme staging cluster.", 275L),
)
private val marketplaceNames = listOf("claude-plugins-official", "superpowers-marketplace", "acme-internal")
private val stems = listOf("sql", "docker", "k8s", "api", "test", "docs", "lint", "deploy", "slack", "jira", "sentry", "figma", "stripe", "aws", "gcp", "redis", "kafka", "graphql", "react", "rust")
private val tails = listOf("helper", "toolkit", "assistant", "inspector", "bridge", "reviewer", "kit")

/** Plugin catalogue for the preview: a few installs across scopes, a generated catalogue, three marketplaces. */
class FakePlugins : PluginService(mock(ClaudeCli::class.java), ExtensionProperties(30, 180, 90)) {
    private val random = Random(7)
    private var installs = mutableListOf(
        install("frontend-design@claude-plugins-official", "1.2.0", ExtensionScope.USER, true),
        install("context7@claude-plugins-official", "1.0.4", ExtensionScope.USER, true),
        install("ponytail@claude-plugins-official", "0.9.1", ExtensionScope.USER, true),
        install("superpowers@superpowers-marketplace", "4.3.1", ExtensionScope.USER, false),
        install("code-review@claude-plugins-official", "1.0.0", ExtensionScope.PROJECT, true, previewProject),
        install("postgres-tools@acme-internal", "0.4.2", ExtensionScope.LOCAL, true, previewProject),
        install("terraform@claude-plugins-official", "2.1.0", ExtensionScope.PROJECT, true, otherProject),
    )
    private val catalog = buildCatalog()
    private val markets = mutableListOf(
        Marketplace("claude-plugins-official", "github", "anthropics/claude-plugins-official", "C:/Users/dev/.claude/plugins/marketplaces/claude-plugins-official"),
        Marketplace("superpowers-marketplace", "github", "obra/superpowers-marketplace", "C:/Users/dev/.claude/plugins/marketplaces/superpowers-marketplace"),
        Marketplace("acme-internal", "git", "https://git.acme.dev/platform/claude-plugins.git", "C:/Users/dev/.claude/plugins/marketplaces/acme-internal"),
    )

    override fun installed(): List<InstalledPlugin> = installs.toList()

    override fun available(): List<AvailablePlugin> = catalog

    override fun refreshAvailable(): List<AvailablePlugin> = catalog

    override fun marketplaces(): List<Marketplace> = markets.toList()

    override fun details(pluginId: String, projectDirectory: java.nio.file.Path?): PluginDetails {
        val seed = Random(pluginId.hashCode().toLong())
        val entry = catalog.firstOrNull { it.pluginId == pluginId }
        val skills = (1..2 + seed.nextInt(4)).map { "${pluginId.substringBefore('@')}-skill-${it}" }
        return PluginDetails(
            (entry?.description ?: "") + " Ships a focused set of skills and agents that load only when they apply.",
            listOf(
                PluginComponent("Skills", skills.size, skills),
                PluginComponent("Agents", 2, listOf("reviewer", "explorer")),
                PluginComponent("Hooks", 1, listOf("PreToolUse")),
                PluginComponent("MCP servers", 1, listOf(pluginId.substringBefore('@') + "-server")),
            ).take(1 + seed.nextInt(4)),
            200 + seed.nextInt(5_200),
        )
    }

    override fun install(pluginId: String, scope: ExtensionScope, projectDirectory: Path?): PluginInstallResult {
        pause()
        if (pluginId == NEEDS_COMMAND_PLUGIN) {
            return PluginInstallResult(false, "", ShownCommand("curl -fsSL https://plugins.acme.dev/install.sh | bash -s -- --plugin acme-deploy --cluster staging", "abc123"))
        }
        return add(pluginId, scope, projectDirectory)
    }

    override fun installAccepting(pluginId: String, scope: ExtensionScope, projectDirectory: Path?, command: ShownCommand): PluginInstallResult {
        pause()
        return add(pluginId, scope, projectDirectory)
    }

    override fun update(pluginId: String, scope: ExtensionScope, projectDirectory: Path?): PluginInstallResult {
        pause()
        if (pluginId == FAILING_PLUGIN) throw ExtensionException("Could not update $pluginId", "error: marketplace acme-internal is unreachable\nfatal: unable to access 'https://git.acme.dev/platform/claude-plugins.git/': Could not resolve host")
        return PluginInstallResult(true, "Updated", null)
    }

    override fun updateAccepting(pluginId: String, scope: ExtensionScope, projectDirectory: Path?, command: ShownCommand) = update(pluginId, scope, projectDirectory)

    override fun uninstall(pluginId: String, scope: ExtensionScope, projectDirectory: Path?) {
        pause()
        installs.removeIf { it.id == pluginId && it.scope == scope && it.projectPath == projectDirectory }
    }

    override fun setEnabled(pluginId: String, scope: ExtensionScope, projectDirectory: Path?, enabled: Boolean) {
        pause()
        installs = installs.map { if (it.id == pluginId && it.scope == scope && it.projectPath == projectDirectory) install(it.id, it.version, it.scope, enabled, it.projectPath) else it }.toMutableList()
    }

    override fun addMarketplace(source: String, scope: ExtensionScope, projectDirectory: Path?) {
        pause()
        markets += Marketplace(source.substringAfterLast('/').removeSuffix(".git"), "github", source, "")
    }

    override fun removeMarketplace(name: String) {
        pause()
        markets.removeIf { it.name == name }
    }

    override fun updateMarketplace(name: String?) = pause()

    private fun add(pluginId: String, scope: ExtensionScope, projectDirectory: Path?): PluginInstallResult {
        installs += install(pluginId, "1.0.0", scope, true, projectDirectory)
        return PluginInstallResult(true, "Installed", null)
    }

    private fun pause() = Thread.sleep(FAKE_DELAY_MILLIS)

    private fun buildCatalog(): List<AvailablePlugin> {
        val real = curated.mapIndexed { index, (name, description, count) ->
            val market = when (name) { "superpowers" -> marketplaceNames[1]; "legacy-linter", "acme-deploy", "postgres-tools" -> marketplaceNames[2]; else -> marketplaceNames[0] }
            AvailablePlugin("$name@$market", name, description, market, count - index)
        }
        val generated = (1..CATALOG_SIZE).map {
            val name = "${stems[random.nextInt(stems.size)]}-${tails[random.nextInt(tails.size)]}"
            val market = marketplaceNames[random.nextInt(marketplaceNames.size)]
            AvailablePlugin("$name-$it@$market", "$name-$it", "Adds ${name.replace('-', ' ')} skills and slash commands to Claude Code sessions.", market, (random.nextDouble().let { x -> x * x * x } * 30_000).toLong())
        }
        return real + generated
    }

    private fun install(id: String, version: String, scope: ExtensionScope, enabled: Boolean, project: Path? = null) =
        InstalledPlugin(id, version, scope, enabled, Path.of("C:/Users/dev/.claude/plugins/cache/$id"), project)
}
