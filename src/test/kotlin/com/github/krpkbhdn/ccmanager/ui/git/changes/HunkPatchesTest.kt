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
        assertThat(changeIndexes(file, hunks[0])).hasSize(3)
    }

    @Test
    fun `a hunk patch carries the file header and the original hunk`() {
        val patch = hunkPatch(file, hunksOf(file)[0])

        assertThat(patch).isEqualTo(
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

            """.trimIndent(),
        )
    }

    @Test
    fun `staging selected lines keeps an unselected removal as context and drops an unselected addition`() {
        val hunk = hunksOf(file)[0]
        val (removed, added1) = changeIndexes(file, hunk)

        val patch = linePatch(file, hunk, setOf(removed, added1), reverse = false)

        assertThat(patch).contains("@@ -1,3 +1,3 @@ class A {")
        assertThat(patch.lines()).contains("-old1", "+new1").doesNotContain("+new2")
    }

    @Test
    fun `an unselected removal becomes context when only additions are staged`() {
        val hunk = hunksOf(file)[0]
        val (_, added1, _) = changeIndexes(file, hunk)

        val patch = linePatch(file, hunk, setOf(added1), reverse = false)

        assertThat(patch).contains("@@ -1,3 +1,4 @@")
        assertThat(patch.lines()).contains(" old1", "+new1").doesNotContain("-old1", "+new2")
    }

    @Test
    fun `unstaging selected lines keeps an unselected addition as context`() {
        val hunk = hunksOf(file)[0]
        val (removed, _, added2) = changeIndexes(file, hunk)

        val patch = linePatch(file, hunk, setOf(removed), reverse = true)

        assertThat(patch).contains("@@ -1,5 +1,4 @@")
        assertThat(patch.lines()).contains("-old1", " new1", " new2").doesNotContain("+new1")
        assertThat(added2).isGreaterThan(removed)
    }

    @Test
    fun `new files are patched against dev null`() {
        val added = SampleRepos.parse(
            """
            diff --git a/N.txt b/N.txt
            new file mode 100644
            --- /dev/null
            +++ b/N.txt
            @@ -0,0 +1,2 @@
            +a
            +b
            """,
        ).single()

        val patch = hunkPatch(added, hunksOf(added).single())

        assertThat(patch).startsWith("diff --git a/N.txt b/N.txt\nnew file mode 100644\n--- /dev/null\n+++ b/N.txt\n@@ -0,0 +1,2 @@")
    }

    @Test
    fun `only added and removed lines are pickable`() {
        assertThat(file.lines.filter(::isChangeLine)).hasSize(5)
    }
}
