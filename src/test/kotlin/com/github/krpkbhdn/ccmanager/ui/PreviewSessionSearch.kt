package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.history.SessionSearchService
import com.github.krpkbhdn.ccmanager.history.SessionSearchService.SearchHit
import java.time.Instant
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** A [SessionSearchService] that answers every query with two canned hits. */
fun previewSessionSearch(): SessionSearchService {
    val service = mock(SessionSearchService::class.java)
    val now = Instant.now()
    val hits = listOf(
        SearchHit("s1", "/preview/other", "/preview/s1.jsonl", "user", now.minusSeconds(3_600), "u1", 0, "...fix the \u0002login\u0003 redirect after..."),
        SearchHit("s2", "/preview/other", "/preview/s2.jsonl", "assistant", now.minusSeconds(86_400), "u2", 0, "The \u0002login\u0003 form posts to /auth and ..."),
    )
    `when`(service.search(anyString(), any(), any(), any())).thenReturn(hits)
    `when`(service.indexedEntryCount()).thenReturn(1_234L)
    return service
}
