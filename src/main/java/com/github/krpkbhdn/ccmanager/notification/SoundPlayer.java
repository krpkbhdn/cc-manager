package com.github.krpkbhdn.ccmanager.notification;

/** Plays a sound without blocking the caller and without ever throwing. */
public interface SoundPlayer {
    void play(NotificationSound sound, double volume);
}
