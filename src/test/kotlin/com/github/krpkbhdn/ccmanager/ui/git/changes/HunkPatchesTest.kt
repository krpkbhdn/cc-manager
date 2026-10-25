package com.github.krpkbhdn.ccmanager.ui.git.changes

import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HunkPatchesTest {
    private val file = SampleRepos.parse(
        """
        diff --git a/A.java b/A.java
        --- a/A.java
        +++ b/A.java
        @@ -1,3 +1,4 @@ class A {
         keep1
        -old1
        +new1
        +new2
         keep2
        @@ -20,2 +21,2 @@ class A {
         keep3
        -old2
        +new3
        """,
    ).single()

    @Test
    fun `hunks are found by their header lines`() {
        val hunks = hunksOf(file)

        assertThat(hunks).hasSize(2)
        assertThat(file.lines[hunks[0].header].text).startsWith("@@ -1,3")
        assertThat(hunks[0].body.first).isEqualTo(hunks[0].header + 1)
        assertThat(hunks[0].body.last).isEqualTo(hunks[1].header - 1)
    }

    @Test
    fun `only added and removed lines are pickable`() {
        assertThat(file.lines.filter(::isChangeLine)).hasSize(5)
    }
}
