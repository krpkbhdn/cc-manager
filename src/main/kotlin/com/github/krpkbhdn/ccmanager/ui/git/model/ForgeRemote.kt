package com.github.krpkbhdn.ccmanager.ui.git.model

private val URL_FORM = Regex("""^([\w+.-]+)://(?:[^/@\s]+@)?([^/:@\s]+)(:\d+)?/(.+)$""")
private val SCP_FORM = Regex("""^(?:[^/\s@]+@)?([^/:@\s]+):(?!//)(.+)$""")
private val SEGMENT = Regex("""[^/:\s]+""")
private const val GITHUB_SEGMENTS = 2
private const val GITHUB_COM = "github.com"

/** Wording and CLI of a code host; [idPrefix] precedes the number of a request ("#7", "!12"). */
enum class Forge(val shortName: String, val longName: String, val cli: String, val cliName: String, val installHint: String, val idPrefix: String) {
    GITHUB("PR", "pull request", "gh", "GitHub CLI", "Install GitHub CLI: https://cli.github.com", "#"),
    GITLAB("MR", "merge request", "glab", "GitLab CLI", "Install GitLab CLI: https://gitlab.com/gitlab-org/cli", "!"),
}

/** [repository] is what the CLI is given: `owner/name` for github.com (`host/owner/name` on any other GitHub host, so gh never falls back to github.com), the full `https://host/group/repo` address for GitLab. */
data class ForgeRemote(val forge: Forge, val host: String, val repository: String) {
    fun signInHint(): String = when (forge) {
        Forge.GITHUB -> "Run `gh auth login` in a terminal"
        Forge.GITLAB -> "Run `glab auth login --hostname $host` in a terminal"
    }
}

/** [webScheme] is http or https for a web URL, null for ssh and scp forms; only a web URL keeps its [port] (":8443"). */
private data class UrlParts(val webScheme: String?, val name: String, val path: String, val port: String = "")

/** The forge a remote URL (https, ssh or scp style) names, null for any other host or an address without a repository path. */
fun forgeRemote(url: String?): ForgeRemote? {
    val trimmed = url?.trim().orEmpty()
    val parts = URL_FORM.find(trimmed)?.destructured?.let { (scheme, name, port, path) ->
        val web = scheme.lowercase().takeIf { it == "http" || it == "https" }
        UrlParts(web, name, path, if (web != null) port else "")
    } ?: SCP_FORM.find(trimmed)?.destructured?.let { (name, path) -> UrlParts(null, name, path) } ?: return null
    val name = parts.name
    val webScheme = parts.webScheme
    val host = name + parts.port
    val segments = parts.path.removeSuffix("/").removeSuffix(".git").split('/')
    if (segments.size < 2 || !segments.all(SEGMENT::matches)) return null
    return when {
        name.contains("github", ignoreCase = true) && segments.size == GITHUB_SEGMENTS -> {
            val ownerAndName = segments.joinToString("/")
            ForgeRemote(Forge.GITHUB, name, if (name.equals(GITHUB_COM, ignoreCase = true)) ownerAndName else "$name/$ownerAndName")
        }
        name.contains("gitlab", ignoreCase = true) -> ForgeRemote(Forge.GITLAB, host, "${webScheme ?: "https"}://$host/${segments.joinToString("/")}")
        else -> null
    }
}
