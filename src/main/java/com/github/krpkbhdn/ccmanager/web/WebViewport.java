package com.github.krpkbhdn.ccmanager.web;

/** The browser viewport a feedback item was left at; {@code deviceName} is the preset name, null for a free size. */
public record WebViewport(int width, int height, String deviceName, DeviceCategory category) {

    static final int MAX_SIDE = 20_000;
    static final int MAX_NAME_CHARS = 100;

    public WebViewport {
        if (width < 1 || width > MAX_SIDE || height < 1 || height > MAX_SIDE) {
            throw new IllegalArgumentException("Viewport size must be 1.." + MAX_SIDE + " pixels.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Viewport category is required.");
        }
        if (deviceName != null && deviceName.length() > MAX_NAME_CHARS) {
            throw new IllegalArgumentException("Device name is too long.");
        }
    }
}
