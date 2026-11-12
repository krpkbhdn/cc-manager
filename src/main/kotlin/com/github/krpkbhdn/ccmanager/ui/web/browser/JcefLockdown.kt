package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.callback.CefJSDialogCallback
import org.cef.callback.CefMediaAccessCallback
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefDownloadHandler
import org.cef.handler.CefJSDialogHandler
import org.cef.handler.CefJSDialogHandlerAdapter
import org.cef.handler.CefPermissionHandler
import org.cef.misc.BoolRef

/**
 * Switches off everything a page could use to reach past its rectangle or to block the host: downloads,
 * camera and microphone prompts, alert/confirm/prompt/beforeunload dialogs and the native context menu.
 * Nothing the page said is logged.
 */
object JcefLockdown {
    fun install(client: CefClient) {
        client.addDownloadHandler(denyDownloads())
        client.addPermissionHandler(denyMediaAccess())
        client.addJSDialogHandler(suppressDialogs())
        client.addContextMenuHandler(noContextMenu())
    }

    private fun denyDownloads() = object : CefDownloadHandler {
        // Without a call to callback.Continue CEF cancels the download.
        override fun onBeforeDownload(b: CefBrowser, item: CefDownloadItem, suggestedName: String, callback: CefBeforeDownloadCallback) = false
        override fun onDownloadUpdated(b: CefBrowser, item: CefDownloadItem, callback: CefDownloadItemCallback) = callback.cancel()
    }

    private fun denyMediaAccess() = object : CefPermissionHandler {
        override fun onRequestMediaAccessPermission(b: CefBrowser, f: CefFrame, requestingUrl: String, requestedPermissions: Int, callback: CefMediaAccessCallback): Boolean {
            callback.Cancel()
            return true
        }
    }

    /** Suppressing makes the dialog return its default (confirm: false) at once, so the page keeps running. */
    private fun suppressDialogs() = object : CefJSDialogHandlerAdapter() {
        override fun onJSDialog(
            b: CefBrowser,
            originUrl: String,
            type: CefJSDialogHandler.JSDialogType,
            message: String,
            defaultPrompt: String,
            callback: CefJSDialogCallback,
            suppress: BoolRef,
        ): Boolean {
            suppress.set(true)
            return false
        }

        override fun onBeforeUnloadDialog(b: CefBrowser, message: String, isReload: Boolean, callback: CefJSDialogCallback): Boolean {
            callback.Continue(true, "")
            return true
        }
    }

    private fun noContextMenu() = object : CefContextMenuHandlerAdapter() {
        override fun onBeforeContextMenu(b: CefBrowser, f: CefFrame, params: CefContextMenuParams, model: CefMenuModel) {
            model.clear()
        }
    }
}
