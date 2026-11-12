package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemKeysTest {
    private val anchor = ElementAnchor("a", "/a", "a", null, null, "", "", "<a>", emptyMap())
    private val item = WebItem(7, 1, 9, 1, anchor, "c", emptyList(), WebItemStatus.OPEN, null, null)
    private val deleted = mutableListOf<WebItem>()
    private val view = WebView(1, FakePageHost()).apply {
        pageItems = listOf(item)
        selectedItemId = 7
    }

    @Test
    fun `plain delete removes the item and clears the selection`() {
        assertThat(ItemKeys.onKey(view, item, Key.Delete, false, deleted::add)).isTrue()
        assertThat(deleted).containsExactly(item)
        assertThat(view.selectedItemId).isNull()
    }

    @Test
    fun `delete with a modifier is ignored`() {
        assertThat(ItemKeys.onKey(view, item, Key.Delete, true, deleted::add)).isFalse()
        assertThat(deleted).isEmpty()
    }

    @Test
    fun `backspace and other keys never delete`() {
        assertThat(ItemKeys.onKey(view, item, Key.Backspace, false, deleted::add)).isFalse()
        assertThat(ItemKeys.onKey(view, item, Key.A, false, deleted::add)).isFalse()
        assertThat(deleted).isEmpty()
        assertThat(view.selectedItemId).isEqualTo(7)
    }
}
