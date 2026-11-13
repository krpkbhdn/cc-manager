package com.github.krpkbhdn.ccmanager.ui.web.browser

import java.net.URI

/** Trust boundary for what the embedded page may open: web pages and local files, never javascript:, data: and the like. */
object NavigationPolicy {
    private val allowedSchemes = setOf("http", "https", "file")
    private val subFrameSchemes = setOf("about", "blob", "data")
    private val scheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    /** What the user may type into the address bar. */
    fun isAllowed(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.equals("about:blank", ignoreCase = true)) return true
        return schemeOf(trimmed) in allowedSchemes
    }

    /**
     * What a page may navigate to on its own (links, redirects, window.open): like [isAllowed], but only a local page may open a local file
     * (navigate to it or embed it); a web page never reaches one. The top level stays on the page's own origin or the local network, so a
     * reviewed dev page cannot pull the open internet into the unsandboxed renderer. Sub-frames also need about:srcdoc, blob: and data: documents.
     */
    fun isAllowedFromPage(url: String, pageUrl: String, mainFrame: Boolean = true): Boolean {
        if (!isAllowed(url)) return !mainFrame && schemeOf(url.trim()) in subFrameSchemes
        if (schemeOf(url.trim()) == FILE) return schemeOf(pageUrl.trim()) == FILE
        return !mainFrame || isSameOriginOrLocalNetwork(url, pageUrl)
    }

    /** scheme://host[:port] of an http(s) URL, safe to log (no path, query or credentials); null for anything else. */
    fun originOf(url: String): String? {
        val uri = parse(url) ?: return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port"
    }

    private fun isSameOriginOrLocalNetwork(url: String, pageUrl: String): Boolean {
        val target = parse(url) ?: return false
        if (isLocalNetworkHost(target.host.lowercase())) return true
        return originKey(target) == parse(pageUrl)?.let(::originKey)
    }

    private fun originKey(uri: URI) = Triple(uri.scheme.lowercase(), uri.host.lowercase(), if (uri.port != -1) uri.port else if (uri.scheme.equals("https", true)) 443 else 80)

    private fun parse(url: String): URI? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme?.lowercase() in webSchemes && !it.host.isNullOrEmpty() }
    }

    private fun isLocalNetworkHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host == "[::1]") return true
        val octets = ipv4.matchEntire(host)?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
        if (octets.any { it > MAX_OCTET }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    fun require(url: String): String {
        require(isAllowed(url)) { "Only http, https, file and about:blank may be opened" }
        return url.trim()
    }

    /** The scheme alone, safe to log where the URL itself may carry page data. */
    fun schemeOf(url: String): String? = scheme.find(url.trim())?.groupValues?.get(1)?.lowercase()

    private const val FILE = "file"
    private const val MAX_OCTET = 255
    private val webSchemes = setOf("http", "https")
    private val ipv4 = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""")
}
