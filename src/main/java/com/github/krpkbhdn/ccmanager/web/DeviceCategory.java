package com.github.krpkbhdn.ccmanager.web;

/** Device class of a viewport, decided by its width in CSS pixels. */
public enum DeviceCategory {
    MOBILE, TABLET, DESKTOP;

    private static final int TABLET_FROM = 600;
    private static final int DESKTOP_FROM = 1024;

    public static DeviceCategory of(int widthCss) {
        if (widthCss < TABLET_FROM) {
            return MOBILE;
        }
        return widthCss < DESKTOP_FROM ? TABLET : DESKTOP;
    }
}
