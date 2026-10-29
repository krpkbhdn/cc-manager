package com.github.krpkbhdn.ccmanager.ui.voice

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DictationEditTest {
    @Test
    fun `an empty field gets the text without spaces`() {
        val applied = DictationEdit(0).apply("", " hello ", commit = false)

        assertThat(applied.text).isEqualTo("hello")
        assertThat(applied.caret).isEqualTo(5)
        assertThat(applied.dictated).isEqualTo(0 until 5)
    }

    @Test
    fun `text at the end is separated by one space`() {
        assertThat(DictationEdit(3).apply("abc", "def", commit = true).text).isEqualTo("abc def")
        assertThat(DictationEdit(4).apply("abc ", "def", commit = true).text).isEqualTo("abc def")
    }

    @Test
    fun `text in the middle keeps the caret after it and a space before what follows`() {
        val applied = DictationEdit(3).apply("abcxyz", "def", commit = true)

        assertThat(applied.text).isEqualTo("abc def xyz")
        assertThat(applied.caret).isEqualTo(7)
    }

    @Test
    fun `a partial is replaced in place by the next partial`() {
        val edit = DictationEdit(3)
        edit.apply("abcxyz", "de", commit = false)

        val applied = edit.apply("abc de xyz", "def gh", commit = false)

        assertThat(applied.text).isEqualTo("abc def gh xyz")
        assertThat(applied.dictated).isEqualTo(3 until 10)
    }

    @Test
    fun `the next utterance goes after a committed one`() {
        val edit = DictationEdit(0)
        val first = edit.apply("", "one", commit = true)

        val second = edit.apply(first.text, "two", commit = false)
        val third = edit.apply(second.text, "two three", commit = true)

        assertThat(third.text).isEqualTo("one two three")
        assertThat(third.caret).isEqualTo(13)
        assertThat(third.dictated).isEqualTo(0 until 13)
    }

    @Test
    fun `an empty utterance leaves the text alone`() {
        assertThat(DictationEdit(3).apply("abcxyz", "  ", commit = true).text).isEqualTo("abcxyz")
    }

    @Test
    fun `only spaces really before the caret are counted`() {
        assertThat(DictationEdit.spacesBefore("ab  ", 4, 3)).isEqualTo(2)
        assertThat(DictationEdit.spacesBefore("ab ", 3, 1)).isEqualTo(1)
        assertThat(DictationEdit.spacesBefore("ab", 2, 1)).isEqualTo(0)
    }

    @Test
    fun `selection replaced before the hold leaves origin at the collapsed caret`() {
        val applied = DictationEdit(2).apply("abef", "cd", commit = true)

        assertThat(applied.text).isEqualTo("ab cd ef")
        assertThat(applied.dictated).isEqualTo(2 until 5)
    }

    @Test
    fun `caret between existing spaces adds no extra space`() {
        val applied = DictationEdit(4).apply("abc  xyz", "def", commit = true)

        assertThat(applied.text).isEqualTo("abc def xyz")
    }

    @Test
    fun `an empty final after a partial removes the partial`() {
        val edit = DictationEdit(3)
        val partial = edit.apply("abcxyz", "de", commit = false)

        val applied = edit.apply(partial.text, "", commit = true)

        assertThat(applied.text).isEqualTo("abcxyz")
        assertThat(applied.caret).isEqualTo(3)
    }

    @Test
    fun `an empty final after a committed utterance changes nothing`() {
        val edit = DictationEdit(0)
        val first = edit.apply("", "one", commit = true)

        assertThat(edit.apply(first.text, "", commit = true).text).isEqualTo("one")
    }

    @Test
    fun `consecutive finals are separated by single spaces only`() {
        val edit = DictationEdit(0)
        var text = ""
        for (word in listOf("one", " two ", "three")) text = edit.apply(text, word, commit = true).text

        assertThat(text).isEqualTo("one two three")
    }

    @Test
    fun `consecutive finals in the middle keep the tail separated`() {
        val edit = DictationEdit(3)
        var text = "abcxyz"
        for (word in listOf("one", "two")) text = edit.apply(text, word, commit = true).text

        assertThat(text).isEqualTo("abc one two xyz")
    }

    @Test
    fun `text shortened under the live segment does not crash`() {
        val edit = DictationEdit(5)
        val partial = edit.apply("hello", "world", commit = false)

        val applied = edit.apply("hi", "again", commit = false)

        assertThat(applied.text).contains("again")
        assertThat(partial.text).isEqualTo("hello world")
    }
}
