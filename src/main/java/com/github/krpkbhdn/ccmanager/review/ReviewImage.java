package com.github.krpkbhdn.ccmanager.review;

public record ReviewImage(long id, long reviewId, int position, String label, String fileName, int width, int height) {
}
