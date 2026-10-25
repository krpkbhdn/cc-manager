package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import tools.jackson.databind.json.JsonMapper

private val mapper = JsonMapper.builder().build()
private val serverName = Regex("[A-Za-z0-9][A-Za-z0-9_.: -]*")
private const val PLUGIN_PREFIX = "plugin:"

enum class McpTransport(val label: String, val json: String, val icon: ImageVector) {
    STDIO("stdio", "stdio", Icons.Terminal),
    HTTP("HTTP", "http", Icons.Globe),
    SSE("SSE", "sse", Icons.Zap),
}

fun transportOf(raw: String): McpTransport = McpTransport.entries.firstOrNull { it.json == raw } ?: McpTransport.STDIO

/** What the add dialog collects for one server; [pairs] are env variables (stdio) or headers (HTTP, SSE). */
data class ServerForm(
    val transport: McpTransport,
    val command: String = "",
    val args: String = "",
    val url: String = "",
    val pairs: List<Pair<String, String>> = emptyList(),
)

/** Same rule as the backend: letters, digits, spaces and '-', '_', '.', ':'; null when the name is fine. */
fun nameProblem(name: String, existing: Collection<String>): String? = when {
    name.isBlank() -> "Give the server a name"
    !serverName.matches(name) -> "Use letters, digits, spaces and - _ . :"
    name in existing -> "A server named $name already exists in this scope"
    else -> null
}

/** Null when the form can be sent; otherwise what is missing. */
fun formProblem(form: ServerForm): String? = when {
    form.transport == McpTransport.STDIO && form.command.isBlank() -> "Enter the command that starts the server"
    form.transport != McpTransport.STDIO && !isHttpUrl(form.url) -> "Enter a full URL starting with http:// or https://"
    else -> null
}

fun jsonProblem(json: String): String? = try {
    if (mapper.readTree(json).isObject) null else "The config must be a JSON object"
} catch (e: RuntimeException) {
    "That is not valid JSON"
}

private fun isHttpUrl(url: String) = url.trim().let { it.startsWith("http://") || it.startsWith("https://") } && url.trim().length > "https://".length

/** The server object for {@code claude mcp add-json}; blank pair rows are dropped. */
fun serverJson(form: ServerForm): String {
    val pairs = form.pairs.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second }
    val config = linkedMapOf<String, Any>("type" to form.transport.json)
    if (form.transport == McpTransport.STDIO) {
        config["command"] = form.command.trim()
        config["args"] = splitArguments(form.args)
        if (pairs.isNotEmpty()) config["env"] = pairs
    } else {
        config["url"] = form.url.trim()
        if (pairs.isNotEmpty()) config["headers"] = pairs
    }
    return mapper.writeValueAsString(config)
}

/** Splits on whitespace; single or double quotes keep their content together. */
fun splitArguments(text: String): List<String> {
    val arguments = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var started = false
    for (c in text) {
        when {
            quote != null && c == quote -> quote = null
            quote == null && (c == '"' || c == '\'') -> { quote = c; started = true }
            quote == null && c.isWhitespace() -> if (started) { arguments += current.toString(); current.clear(); started = false }
            else -> { current.append(c); started = true }
        }
    }
    if (started) arguments += current.toString()
    return arguments
}

/** A server shown in the list: configured by the user or the project, or inherited, or provided by a plugin. */
data class McpEntry(
    val name: String,
    val scope: ExtensionScope?,
    val transport: McpTransport?,
    val target: String,
    val approval: McpApproval,
    val inherited: Boolean,
) {
    val fromPlugin: Boolean get() = scope == null
    val key: String get() = mcpKey(scope, name)
    val removable: Boolean get() = !inherited && !fromPlugin
    val displayName: String get() = if (fromPlugin) name.removePrefix(PLUGIN_PREFIX).substringAfter(':') else name
    val pluginName: String get() = name.removePrefix(PLUGIN_PREFIX).substringBefore(':')
}

data class McpGroups(val own: List<McpEntry>, val inherited: List<McpEntry>, val plugins: List<McpEntry>) {
    val size: Int get() = own.size + inherited.size + plugins.size
}

/** Own servers (project before local), user servers inherited into a project, and plugin servers seen only in [health]. */
fun mcpGroups(servers: List<McpServerConfig>, health: List<McpHealth>, context: ExtensionContext): McpGroups {
    val entries = servers.filter { context.sees(it.scope, context.projectPath) }.map {
        McpEntry(it.name, it.scope, transportOf(it.transport), it.target, it.approval, inherited = !context.owns(it.scope, context.projectPath))
    }
    val plugins = health.filter { it.fromPlugin() }.map { McpEntry(it.name, null, null, it.target, McpApproval.NOT_REQUIRED, inherited = false) }
    return McpGroups(
        entries.filterNot { it.inherited }.sortedWith(compareBy<McpEntry> { it.scope }.thenBy { it.name.lowercase() }),
        entries.filter { it.inherited }.sortedBy { it.name.lowercase() },
        plugins.sortedBy { it.name.lowercase() },
    )
}

fun mcpKey(scope: ExtensionScope?, name: String) = "${scope ?: "plugin"}:$name"

private val secretSection = Regex("""^(\s*)(Environment|Headers):\s*$""")
private val queryValue = Regex("""([?&][^=&#\s]+)=[^&#\s]*""")
private val urlCredentials = Regex("""(?<=//)[^/@\s]+@""")
private val secretFlag = Regex("""(--?[\w-]*(?:token|key|secret|password)[\w-]*)(=|\s+)\S+""", RegexOption.IGNORE_CASE)
const val MASK = "••••••"

/** Hides what {@code claude mcp get} prints about env variables, headers and URL credentials or query values, and the value of stdio flags such as --token. */
fun maskSecrets(text: String): String {
    var sectionIndent = -1
    return text.lines().joinToString("\n") { line ->
        val indent = line.length - line.trimStart().length
        val header = secretSection.matchEntire(line)
        when {
            header != null -> { sectionIndent = header.groupValues[1].length; line }
            sectionIndent >= 0 && line.isNotBlank() && indent > sectionIndent -> maskEntry(line)
            else -> { sectionIndent = -1; line.replace(urlCredentials, "$MASK@").replace(queryValue, "$1=$MASK").replace(secretFlag, "$1$2$MASK") }
        }
    }
}

private fun maskEntry(line: String): String {
    val separator = line.indexOfFirst { it == '=' || it == ':' }
    if (separator < 0) return line
    return line.substring(0, separator + 1) + (if (line[separator] == ':') " " else "") + MASK
}
