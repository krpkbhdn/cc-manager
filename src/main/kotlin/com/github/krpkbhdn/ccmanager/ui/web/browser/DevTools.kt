package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.cef.browser.CefBrowser
import java.util.concurrent.CompletableFuture

/** The Chrome DevTools protocol of one browser: `call` resolves with the raw JSON of the result. JCEF keeps one client per browser. */
class DevTools(private val browser: CefBrowser) {
    fun call(method: String, paramsJson: String = "{}"): CompletableFuture<String> =
        browser.devToolsClient.executeDevToolsMethod(method, paramsJson)
}
