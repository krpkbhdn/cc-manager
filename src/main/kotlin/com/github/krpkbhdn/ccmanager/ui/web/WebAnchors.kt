package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.StyleChange as StoredStyleChange

/** Mapping between what the page reports and what the backend stores. */
internal fun ElementTarget.toAnchor(): ElementAnchor = ElementAnchor(
    selector, xpath, tag,
    sourceHint?.let { ElementAnchor.SourceHint(it.component, it.file, it.line) },
    rect?.let { ElementAnchor.Rect(it.x, it.y, it.w, it.h) },
    frame.orEmpty(), text, outerHtml, computed,
)

/** Enough of the picked element to show and edit a stored item again; live-only parts (classes, box model) stay empty. */
internal fun ElementAnchor.toTarget(): ElementTarget = ElementTarget(
    selector = selector(), xpath = xpath().orEmpty(), tag = tag().orEmpty(), id = "", classes = emptyList(),
    text = text().orEmpty(), outerHtml = outerHtml().orEmpty(),
    rect = rect()?.let { Rect(it.x(), it.y(), it.w(), it.h()) }, viewportRect = null,
    computed = computed(), box = null,
    sourceHint = sourceHint()?.let { SourceHint(it.component(), it.file(), it.line()) },
    frame = frame()?.takeIf { it.isNotEmpty() }, pageUrl = null,
)

internal fun ElementAnchor.toPinAnchor(): AnnotationAnchor =
    AnnotationAnchor(selector(), xpath().orEmpty(), text().orEmpty(), tag().orEmpty(), rect()?.let { Rect(it.x(), it.y(), it.w(), it.h()) })

/** The pin carries the item's stable number so the overlay and the list agree. */
internal fun WebItem.toPin(): AnnotationPin = AnnotationPin(id().toString(), n(), anchor().toPinAnchor(), status().name.lowercase())

internal fun List<StyleChange>.toStored(): List<StoredStyleChange> = map { StoredStyleChange(it.property, it.from, it.to) }

/** Property to edited value of a stored item's changes. */
internal fun WebItem.editedValues(): Map<String, String> = styleChanges().associate { it.property() to it.to() }
