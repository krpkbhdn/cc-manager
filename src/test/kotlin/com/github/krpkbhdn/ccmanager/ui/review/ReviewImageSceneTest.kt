package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.review.MarkKind
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewImageSceneTest {
    private val shots = Path.of(System.getProperty("ccm.shots", "C:/Users/Bohdan/Work/Code/Personal/ccm-shots/review"))

    @Test
    fun `a right click on the image opens the drawing menu`() {
        ReviewImageScene().use { scene ->
            val before = scene.png()
            scene.click(scene.screen(0.5, 0.2), PointerButton.Secondary)
            scene.frames(10)
            val after = scene.png()
            Files.createDirectories(shots)
            Files.write(shots.resolve("review-context-menu-test.png"), after)

            assertThat(after.contentEquals(before)).isFalse()
        }
    }

    @Test
    fun `dragging a rectangle by its outline moves it, outside and inside the stroke`() {
        listOf(Offset(-6f, 0f), Offset(6f, 0f)).forEach { nudge ->
            ReviewImageScene().use { scene ->
                val original = scene.state.marks(scene.imageId)[0]
                val edge = scene.screen(original.x, original.y + original.h / 2) + nudge
                scene.drag(edge, edge + Offset(0f, 60f))

                assertThat(scene.state.marks(scene.imageId)[0].y).isGreaterThan(original.y)
                assertThat(scene.state.marks(scene.imageId)[0].h).isEqualTo(original.h)
            }
        }
    }

    @Test
    fun `dragging an ellipse by its outline moves it`() {
        ReviewImageScene().use { scene ->
            val original = scene.state.marks(scene.imageId)[1]
            val edge = scene.screen(original.x, original.y + original.h / 2) + Offset(-4f, 0f)
            scene.drag(edge, edge + Offset(0f, 40f))

            assertThat(scene.state.marks(scene.imageId)[1].y).isGreaterThan(original.y)
        }
    }

    private fun movedBy(index: Int, nx: Double, ny: Double, nudge: Offset = Offset.Zero) = ReviewImageScene().use { scene ->
        val original = scene.state.marks(scene.imageId)[index]
        scene.state.selectedMark = index
        scene.frames()
        val from = scene.screen(nx, ny) + nudge
        scene.drag(from, from + Offset(30f, 20f))
        val after = scene.state.marks(scene.imageId)[index]
        assertThat(after.w).isEqualTo(original.w, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(after.h).isEqualTo(original.h, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(after.x).isGreaterThan(original.x)
        assertThat(after.y).isGreaterThan(original.y)
    }

    @Test
    fun `a selected rectangle moves when dragged by a non-handle outline point or just outside it`() {
        movedBy(0, 0.22 + 0.085, 0.30)
        movedBy(0, 0.22 + 0.085, 0.30, Offset(0f, -6f))
        movedBy(0, 0.22 + 0.085, 0.30, Offset(0f, 6f))
    }

    @Test
    fun `a selected ellipse moves when dragged by a point on its curve`() {
        val k = Math.sqrt(0.5)
        movedBy(1, 0.77 - 0.07 * k, 0.68 - 0.06 * k)
    }

    @Test
    fun `a selected freeform inserts a vertex on an edge and moves from inside`() {
        ReviewImageScene().use { scene ->
            scene.state.selectedMark = 3
            scene.frames()
            val from = scene.screen(0.55, 0.69)
            scene.drag(from, from + Offset(0f, 30f))
            assertThat(scene.state.marks(scene.imageId)[3].points).hasSize(6)
        }
        movedBy(3, 0.5, 0.77)
    }

    @Test
    fun `right button press with a ready mark shows the Select entry`() {
        ReviewImageScene().use { scene ->
            scene.pointer(androidx.compose.ui.input.pointer.PointerEventType.Move, scene.screen(0.3, 0.38))
            scene.pointer(androidx.compose.ui.input.pointer.PointerEventType.Press, scene.screen(0.3, 0.38), PointerButton.Secondary)
            scene.frames(10)
            Files.createDirectories(shots)
            Files.write(shots.resolve("review-context-menu-mark-test.png"), scene.png())
        }
    }

    private val ctrl = PointerKeyboardModifiers(isCtrlPressed = true)

    @Test
    fun `ctrl plus left drag pans the image and creates or moves no mark`() {
        ReviewImageScene().use { scene ->
            val marksBefore = scene.state.marks(scene.imageId)
            val offsetBefore = scene.viewer.viewport!!.offsetX
            val on = scene.screen(marksBefore[0].x + marksBefore[0].w / 2, marksBefore[0].y + marksBefore[0].h / 2)
            scene.drag(on, on + Offset(40f, 25f), modifiers = ctrl)

            assertThat(scene.viewer.viewport!!.offsetX).isNotEqualTo(offsetBefore)
            assertThat(scene.state.marks(scene.imageId)).isEqualTo(marksBefore)
        }
    }

    @Test
    fun `middle button drag pans too`() {
        ReviewImageScene().use { scene ->
            val offsetBefore = scene.viewer.viewport!!.offsetY
            scene.drag(Offset(300f, 300f), Offset(300f, 360f), PointerButton.Tertiary)

            assertThat(scene.viewer.viewport!!.offsetY).isNotEqualTo(offsetBefore)
        }
    }

    @Test
    fun `a ctrl click without movement does nothing`() {
        ReviewImageScene().use { scene ->
            val marksBefore = scene.state.marks(scene.imageId)
            val viewportBefore = scene.viewer.viewport
            val at = scene.screen(marksBefore[0].x + marksBefore[0].w / 2, marksBefore[0].y + marksBefore[0].h / 2)
            scene.state.selectedMark = null
            scene.pointer(PointerEventType.Move, at, modifiers = ctrl)
            scene.pointer(PointerEventType.Press, at, PointerButton.Primary, ctrl)
            scene.pointer(PointerEventType.Release, at, PointerButton.Primary, ctrl)

            assertThat(scene.state.selectedMark).isNull()
            assertThat(scene.state.marks(scene.imageId)).isEqualTo(marksBefore)
            assertThat(scene.viewer.viewport).isEqualTo(viewportBefore)
        }
    }

    private val corners = listOf(0.1 to 0.1, 0.4 to 0.1, 0.4 to 0.4, 0.1 to 0.4)

    private fun freeformScene(block: (ReviewImageScene) -> Unit) = ReviewImageScene().use { scene ->
        scene.state.arm(ReviewTool.FREEFORM)
        block(scene)
    }

    private fun ReviewImageScene.clickAll(points: List<Pair<Double, Double>>) = points.forEach { click(screen(it.first, it.second)) }

    private fun ReviewImageScene.freeforms() = state.marks(imageId).filter { it.kind == MarkKind.FREEFORM }

    @Test
    fun `four clicks and a click on the first point make one freeform mark and clear the draft`() = freeformScene { scene ->
        val before = scene.freeforms().size
        scene.clickAll(corners)
        assertThat(scene.state.draft.points).hasSize(4)
        scene.click(scene.screen(0.1, 0.1) + Offset(4f, 3f))

        assertThat(scene.freeforms()).hasSize(before + 1)
        assertThat(scene.freeforms().last().points).hasSize(4)
        assertThat(scene.state.draft.isEmpty).isTrue()
        assertThat(scene.state.oneShotTool).isNull()
        assertThat(scene.state.selectedMark).isEqualTo(scene.state.marks(scene.imageId).lastIndex)
    }

    @Test
    fun `a click on the first point with fewer than three points creates nothing`() = freeformScene { scene ->
        val before = scene.state.marks(scene.imageId).size
        scene.clickAll(listOf(0.1 to 0.1, 0.4 to 0.1))
        scene.click(scene.screen(0.1, 0.1))

        assertThat(scene.state.marks(scene.imageId)).hasSize(before)
        assertThat(scene.state.draft.points).hasSize(2)
    }

    @Test
    fun `Escape cancels the draft and the one-shot, Backspace removes the last point`() = freeformScene { scene ->
        scene.clickAll(corners)
        scene.key(Key.Backspace)
        assertThat(scene.state.draft.points).hasSize(3)

        scene.key(Key.Escape)
        assertThat(scene.state.draft.isEmpty).isTrue()
        assertThat(scene.state.oneShotTool).isNull()
    }

    @Test
    fun `Enter closes a draft of three points`() = freeformScene { scene ->
        val before = scene.freeforms().size
        scene.clickAll(corners.take(3))
        scene.key(Key.Enter)

        assertThat(scene.freeforms()).hasSize(before + 1)
        assertThat(scene.state.draft.isEmpty).isTrue()
    }

    @Test
    fun `a ctrl drag in the middle of a draft pans without adding a point`() = freeformScene { scene ->
        scene.clickAll(corners.take(2))
        val offset = scene.viewer.viewport!!.offsetX
        scene.drag(Offset(500f, 300f), Offset(540f, 330f), modifiers = ctrl)

        assertThat(scene.state.draft.points).hasSize(2)
        assertThat(scene.viewer.viewport!!.offsetX).isNotEqualTo(offset)
    }

    @Test
    fun `a drag with the freeform tool places one point and creates no mark`() = freeformScene { scene ->
        val before = scene.state.marks(scene.imageId).size
        scene.drag(scene.screen(0.1, 0.1), scene.screen(0.5, 0.5))

        assertThat(scene.state.marks(scene.imageId)).hasSize(before)
        assertThat(scene.state.draft.points).hasSize(1)
    }

    @Test
    fun `a right click cancels the draft instead of opening the menu`() = freeformScene { scene ->
        scene.clickAll(corners.take(2))
        scene.click(scene.screen(0.6, 0.6), PointerButton.Secondary)

        assertThat(scene.state.draft.isEmpty).isTrue()
        assertThat(scene.state.oneShotTool).isNull()
    }
}
