package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageScroll
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.PageKey
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebViewport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job

enum class WebPanelTab { INSPECTOR, ITEMS }

/** The stored key of a page address, or null for one that cannot be reviewed (about:blank, ftp and the like). */
internal fun pageKeyOf(url: String): String? = try { PageKey.of(url) } catch (_: IllegalArgumentException) { null }

/** Path of an address for a chip; the whole text when it is not a URI. */
internal fun draftPageLabel(url: String): String = runCatching { java.net.URI(url).path?.takeIf { it.isNotEmpty() } }.getOrNull() ?: url

/** Everything of a draft that Save takes with it, so the draft can be cleared before the save finishes and put back if it fails. */
internal class DraftSnapshot(
    val target: ElementTarget,
    val editing: WebItem?,
    val edits: Map<String, String>,
    val comment: String,
    val pageUrl: String,
    val pageTitle: String,
    val shot: ByteArray?,
    val viewport: WebViewport?,
)

/** A pointer position as the page point (CSS px) and the canvas offset (px). */
data class PointerSpot(val page: Offset, val canvas: Offset)

/** The pin under the pointer: its item id and the element's page rectangle. */
data class ItemPopoverState(val itemId: String, val rect: Rect?)

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/** What the user typed in the address field as a loadable URL: a bare host gets `http://`. */
fun normalizeUrl(typed: String): String = typed.trim().let { if (it.isEmpty() || SCHEME.containsMatchIn(it)) it else "http://$it" }

/** Observable state of one project's browser: the page, the inspector's picked element and its draft, console and panel choices. */
class WebView(val projectId: Long, val host: WebPageHost) {
    val address = TextFieldState()
    val comment = TextFieldState()
    val edits = mutableStateMapOf<String, String>()
    var toggles by mutableStateOf(InspectorToggles())
    var hovered by mutableStateOf<ElementTarget?>(null)
    var device by mutableStateOf(DeviceSelection.Responsive)
    /** The page's current layout, reported by the overlay; what a new item records as its viewport. */
    var layout by mutableStateOf<PageViewport?>(null)
    var contextMenu by mutableStateOf<ContextMenuState?>(null)
    var itemPopover by mutableStateOf<ItemPopoverState?>(null)
    /** False = page locked: the canvas sends no mouse or key input to the page, the inspector is driven by the host instead. */
    var interaction by mutableStateOf(true)
    /** The focused page element edits text (from the inspector); plain hotkeys stay out of the way. Reset on navigation. */
    var pageEditing by mutableStateOf(false)
    /** Last pointer position over the canvas; written by WebCanvas on every move, not observable. */
    var lastPointer: PointerSpot? = null
    /** Picking stays on after an element is picked (set by the Shift+I key or Shift+click on Pick). */
    var stickyPick by mutableStateOf(false)
    /** A screenshot is being taken; the screenshot actions wait for it. */
    var shooting by mutableStateOf(false)
    /** The region-select overlay is up over the canvas. */
    var regionSelecting by mutableStateOf(false)

    /** The page scroll read when the region overlay opened; the selected region is relative to it. Null until read. */
    @Volatile var regionScroll: PageScroll? = null
    /** Some element of the Web screen holds keyboard focus (the canvas included); written by the screen, not observable. */
    @Volatile var screenFocused = false
    var picked by mutableStateOf<ElementTarget?>(null)
        private set
    var editingItem by mutableStateOf<WebItem?>(null)
        private set

    /** Changes whenever the draft is replaced, so the editable fields restart from the new values. */
    var draftGeneration by mutableIntStateOf(0)
        private set
    /** The page the draft was picked on; it may differ from [pageKey] when the page navigated meanwhile. */
    var draftPageKey by mutableStateOf<String?>(null)
        private set
    var draftPageUrl = ""
        private set
    var draftPageTitle = ""
        private set
    var draftSaving by mutableStateOf(false)
    internal var shown = false
    internal var lastShownMillis = 0L
    var pageUrl by mutableStateOf("")
    var pageTitle by mutableStateOf("")
    var pageKey by mutableStateOf<String?>(null)
    var pageItems by mutableStateOf<List<WebItem>>(emptyList())
    var selectedItemId by mutableStateOf<Long?>(null)
    var panelTab by mutableStateOf(WebPanelTab.INSPECTOR)
    var consoleOpen by mutableStateOf(false)
    var consoleFilter by mutableStateOf(ConsoleLevel.entries.toSet())
    var consoleLines by mutableStateOf<List<ConsoleLine>>(emptyList())
        private set
    internal var pendingFocus: Long? = null
    internal val jobs = CopyOnWriteArrayList<Job>()

    /** PNG of the picked element taken at pick time, so it matches what the user saw even if the page scrolls before saving. */
    @Volatile internal var pickShot: ByteArray? = null

    /** The viewport at pick time, so a device change before Save does not mislabel the item. */
    internal var pickViewport: WebViewport? = null
    private val consoleBuffer = ConsoleBuffer()

    /** The items of the page that belong to the selected device scope; pins and the list show only these. */
    val visibleItems: List<WebItem> get() = pageItems.visibleIn(device.scope)

    fun hotKeyModes() = HotKeyModes(interaction, toggles.pick, contextMenu != null, regionSelecting)

    /** Not while another element of the screen has focus, a menu, popover or the region overlay is up, or a Swing text component holds it. */
    fun canGrabCanvasFocus() = !screenFocused && contextMenu == null && itemPopover == null && !regionSelecting &&
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner !is javax.swing.text.JTextComponent

    /** The middle of the page layout in canvas px, where a popover goes when nothing better is known. */
    internal fun layoutCentre(): Offset = layout?.let { Offset(it.widthCss / 2f, it.heightCss / 2f) } ?: Offset.Zero

    fun currentViewport(): WebViewport? = layout?.let { WebViewport(it.widthCss, it.heightCss, device.preset?.name, device.scope ?: DeviceCategory.of(it.widthCss)) }

    val styleChanges: List<StyleChange> get() = picked?.let { StyleDiff.changes(it.computed, edits) }.orEmpty()

    /** Typed work that a new pick must not overwrite. */
    val draftDirty: Boolean get() = comment.text.isNotBlank() || edits.isNotEmpty()

    /** An item can be saved when it says something or changes a style. */
    val canSave: Boolean get() = picked != null && (comment.text.isNotBlank() || styleChanges.isNotEmpty())

    fun addConsole(line: ConsoleLine) {
        consoleBuffer.add(line)
        consoleLines = consoleBuffer.lines()
    }

    fun clearConsole() {
        consoleBuffer.clear()
        consoleLines = emptyList()
    }

    fun toggleLevel(level: ConsoleLevel) {
        consoleFilter = if (level in consoleFilter) consoleFilter - level else consoleFilter + level
    }

    fun beginDraft(target: ElementTarget) = replaceDraft(target, emptyMap(), "", null, pageUrl, pageTitle)

    fun beginEdit(item: WebItem) = replaceDraft(item.anchor().toTarget(), item.editedValues(), item.comment(), item, "", "")

    fun clearDraft() = replaceDraft(null, emptyMap(), "", null, "", "")

    internal fun snapshotDraft(): DraftSnapshot? =
        picked?.let { DraftSnapshot(it, editingItem, edits.toMap(), comment.text.toString(), draftPageUrl, draftPageTitle, pickShot, pickViewport) }

    internal fun restoreDraft(draft: DraftSnapshot) {
        replaceDraft(draft.target, draft.edits, draft.comment, draft.editing, draft.pageUrl, draft.pageTitle)
        pickShot = draft.shot
        pickViewport = draft.viewport
    }

    private fun replaceDraft(target: ElementTarget?, values: Map<String, String>, text: String, item: WebItem?, url: String, title: String) {
        draftPageUrl = url
        draftPageTitle = title
        draftPageKey = pageKeyOf(url)
        picked = target
        pickShot = null
        pickViewport = null
        editingItem = item
        edits.clear()
        edits.putAll(values)
        if (text.isEmpty()) comment.clearText() else comment.setTextAndPlaceCursorAtEnd(text)
        draftGeneration++
        if (target != null) panelTab = WebPanelTab.INSPECTOR
    }
}
