package com.github.krpkbhdn.ccmanager.web;

import java.util.List;

/**
 * {@code n} is the display number: unique among the project's existing items at creation time, so it may be reused after the highest
 * item is deleted. {@code screenshotFile} is null until one is saved; {@code viewport} is null for items left before it was recorded.
 */
public record WebItem(long id, long revisionId, long pageId, int n, ElementAnchor anchor, String comment, List<StyleChange> styleChanges,
        WebItemStatus status, String screenshotFile, WebViewport viewport) {

    public WebItem {
        styleChanges = styleChanges == null ? List.of() : List.copyOf(styleChanges);
    }
}
