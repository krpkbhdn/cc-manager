package com.github.krpkbhdn.ccmanager.ui.web.browser

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
     * What a page may navigate to on its own (links, redirects, window.open): like [isAllowed], but a page that is
     * not itself a local file may not reach local files. Sub-frames also need about:srcdoc, blob: and data: documents.
     */
    fun isAllowedFromPage(url: String, pageUrl: String, mainFrame: Boolean = true): Boolean {
        if (!isAllowed(url)) return !mainFrame && schemeOf(url.trim()) in subFrameSchemes
        return schemeOf(url.trim()) != FILE || schemeOf(pageUrl.trim()) == FILE
    }

    fun require(url: String): String {
        require(isAllowed(url)) { "Only http, https, file and about:blank may be opened" }
        return url.trim()
    }

    /** The scheme alone, safe to log where the URL itself may carry page data. */
    fun schemeOf(url: String): String? = scheme.find(url.trim())?.groupValues?.get(1)?.lowercase()

    private const val FILE = "file"
}
