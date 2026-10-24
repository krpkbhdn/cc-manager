package com.github.krpkbhdn.ccmanager.git;

import java.util.List;

/**
 * Open lanes of the commit graph after some row: what each lane waits for (null = free) and the next colour slot.
 * Passing it from one page to the next keeps infinite scroll consistent without re-reading earlier pages.
 */
public record GraphState(List<Lane> lanes, int nextColor) {

    /** A lane waiting for {@code hash} (the next commit it will reach), drawn in palette slot {@code color}. */
    public record Lane(String hash, int color) {}

    public static final GraphState EMPTY = new GraphState(List.of(), 0);
}
