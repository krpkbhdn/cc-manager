package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.unit.Density
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val ESC = "\u001b"
private const val WIDTH = 1000
private const val HEIGHT = 640
private const val CELL_WIDTH = 8
private const val PADDING_X = 12
private const val PADDING_Y = 8
private const val FRAME_NANOS = 16_000_000L
private const val FIT_FRAMES = 15
private const val CLICK_GAP_MILLIS = 30L
private const val MOUSE_PRESS_REPORTING = "$ESC[?1000h$ESC[?1006h"
private const val MOUSE_DRAG_REPORTING = "$ESC[?1002h$ESC[?1006h"
private const val MOUSE_ANY_MOTION_REPORTING = "$ESC[?1003h$ESC[?1006h"
private const val META_FLAG = 8
private const val CTRL_FLAG = 16
private const val DRAG_FLAG = 32
private const val WHEEL_UP = 64
private const val WHEEL_DOWN = 65
private const val MIDDLE_BUTTON = 1

/** Mouse handling of the terminal view: selection gestures and the bytes an application receives in mouse mode. */
@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class TerminalPointerTest {
    private val connector = ScriptedConnector()
    private val session = TerminalSession(connector)
    private val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { TerminalSessionView(session, Modifier, TerminalTheme()) }
    private val cellHeight = TerminalFonts(8).cellHeight
    private var nanos = 0L
    private var clock = 0L

    init {
        repeat(FIT_FRAMES) { frame() }
        settle(connector, session)
    }

    @AfterEach
    fun close() {
        scene.close()
        session.close()
    }

    private fun frame() {
        nanos += FRAME_NANOS
        scene.render(nanos)
    }

    private fun play(text: String) {
        connector.feed(text)
        settle(connector, session)
        repeat(4) { frame() }
    }

    private fun point(column: Int, row: Int) = Offset((PADDING_X + column * CELL_WIDTH + 2).toFloat(), (PADDING_Y + row * cellHeight + 2).toFloat())

    private fun pointer(
        type: PointerEventType,
        at: Offset,
        button: PointerButton? = null,
        held: PointerButton? = null,
        scroll: Offset = Offset.Zero,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ) {
        clock += CLICK_GAP_MILLIS
        val buttons = PointerButtons(
            isPrimaryPressed = held == PointerButton.Primary,
            isSecondaryPressed = held == PointerButton.Secondary,
            isTertiaryPressed = held == PointerButton.Tertiary,
        )
        val keys = PointerKeyboardModifiers(isCtrlPressed = ctrl, isAltPressed = alt, isShiftPressed = shift)
        scene.sendPointerEvent(type, at, scroll, clock, buttons = buttons, keyboardModifiers = keys, button = button)
    }

    private fun press(at: Offset, button: PointerButton = PointerButton.Primary, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        pointer(PointerEventType.Press, at, button, held = button, ctrl = ctrl, alt = alt, shift = shift)

    private fun release(at: Offset, button: PointerButton = PointerButton.Primary, shift: Boolean = false) =
        pointer(PointerEventType.Release, at, button, shift = shift)

    private fun drag(at: Offset, held: PointerButton = PointerButton.Primary, shift: Boolean = false) =
        pointer(PointerEventType.Move, at, held = held, shift = shift)

    private fun clickAt(at: Offset) {
        press(at)
        release(at)
    }

    private fun click(column: Int, row: Int) {
        press(point(column, row))
        release(point(column, row))
    }

    private fun wheel(at: Offset, delta: Float, ctrl: Boolean = false, shift: Boolean = false) =
        pointer(PointerEventType.Scroll, at, scroll = Offset(0f, delta), ctrl = ctrl, shift = shift)

    private fun written(): String {
        settle(connector, session)
        return connector.writtenText()
    }

    private fun reportedClick(column: Int, row: Int) = sgr(0, column, row) + sgr(0, column, row, 'm')

    private fun sgr(code: Int, column: Int, row: Int, final: Char = 'M') = "$ESC[<$code;${column + 1};${row + 1}$final"

    @Test
    fun dragSelectsFromTheAnchorToTheCellUnderThePointer() {
        play("${ESC}[2J${ESC}[Hhello brave new world")
        press(point(0, 0))
        drag(point(4, 0))
        assertThat(session.selection).isEqualTo(CellSelection(Cell(0, 0), Cell(4, 0)))
        drag(point(10, 0))
        release(point(10, 0))
        assertThat(session.selection).isEqualTo(CellSelection(Cell(0, 0), Cell(10, 0)))
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("hello brave")
    }

    @Test
    fun movingWithinTheAnchorCellStartsNoSelection() {
        play("${ESC}[2J${ESC}[Hhello")
        press(Offset(point(3, 1).x, point(3, 1).y))
        drag(Offset(point(3, 1).x + 3, point(3, 1).y + 1))
        release(point(3, 1))
        assertThat(session.selection).isNull()
    }

    @Test
    fun draggingBackwardKeepsTheAnchorAndSelectsTheText() {
        play("${ESC}[2J${ESC}[Hhello brave")
        press(point(10, 0))
        drag(point(6, 0))
        release(point(6, 0))
        assertThat(session.selection).isEqualTo(CellSelection(Cell(10, 0), Cell(6, 0)))
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("brave")
    }

    @Test
    fun aPlainClickClearsAnExistingSelection() {
        play("${ESC}[2J${ESC}[Hhello")
        session.select(CellSelection(Cell(0, 0), Cell(3, 0)))
        click(20, 5)
        assertThat(session.selection).isNull()
    }

    @Test
    fun doubleClickSelectsAWordAndTripleClickTheLine() {
        play("${ESC}[2J${ESC}[Hhello brave new world")
        click(8, 0)
        click(8, 0)
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("brave")
        click(8, 0)
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("hello brave new world")
    }

    @Test
    fun clickingAnotherCellRestartsTheClickCount() {
        play("${ESC}[2J${ESC}[Hhello brave new world")
        click(8, 0)
        click(8, 0)
        assertThat(session.selection).isNotNull()
        click(20, 0)
        assertThat(session.selection).isNull()
    }

    @Test
    fun shiftClickExtendsTheSelectionHeadAndKeepsTheAnchor() {
        play("${ESC}[2J${ESC}[Hhello brave new world")
        press(point(0, 0))
        drag(point(4, 0))
        release(point(4, 0))
        press(point(15, 0), shift = true)
        release(point(15, 0), shift = true)
        assertThat(session.selection).isEqualTo(CellSelection(Cell(0, 0), Cell(15, 0)))
    }

    @Test
    fun shiftClickWithoutSelectionBehavesLikeAPlainClick() {
        play("${ESC}[2J${ESC}[Hhello")
        press(point(2, 0), shift = true)
        release(point(2, 0), shift = true)
        assertThat(session.selection).isNull()
    }

    @Test
    fun rightClickKeepsTheSelectionAndStartsNoNewOne() {
        play("${ESC}[2J${ESC}[Hhello brave")
        session.select(CellSelection(Cell(0, 0), Cell(4, 0)))
        press(point(10, 0), PointerButton.Secondary)
        drag(point(14, 0), held = PointerButton.Secondary)
        release(point(14, 0), PointerButton.Secondary)
        assertThat(session.selection).isEqualTo(CellSelection(Cell(0, 0), Cell(4, 0)))
    }

    @Test
    fun movingWithoutAButtonDoesNotSelectOrReportOutsideMouseMode() {
        play("${ESC}[2J${ESC}[Hhello")
        pointer(PointerEventType.Move, point(3, 0))
        pointer(PointerEventType.Move, point(6, 0))
        assertThat(session.selection).isNull()
        assertThat(written()).isEmpty()
    }

    @Test
    fun applicationInMouseModeGetsPressAndReleaseAtOneBasedCells() {
        play(MOUSE_PRESS_REPORTING)
        click(4, 2)
        assertThat(written()).isEqualTo(sgr(0, 4, 2) + sgr(0, 4, 2, 'm'))
        assertThat(session.selection).isNull()
    }

    @Test
    fun cellBoundariesDecideTheReportedColumnAndRow() {
        play(MOUSE_PRESS_REPORTING)
        val firstPixelX = PADDING_X + 5 * CELL_WIDTH
        val firstPixelY = PADDING_Y + 3 * cellHeight
        clickAt(Offset(firstPixelX.toFloat(), firstPixelY.toFloat()))
        clickAt(Offset(firstPixelX - 1f, firstPixelY - 1f))
        clickAt(Offset(firstPixelX + CELL_WIDTH - 1f, firstPixelY + cellHeight - 1f))
        assertThat(written()).isEqualTo(reportedClick(5, 3) + reportedClick(4, 2) + reportedClick(5, 3))
    }

    @Test
    fun pointsInThePaddingAndBeyondTheGridClampToTheEdgeCells() {
        play(MOUSE_PRESS_REPORTING)
        clickAt(Offset(0f, 0f))
        clickAt(Offset(WIDTH.toFloat() - 1, HEIGHT.toFloat() - 1))
        assertThat(written()).isEqualTo(reportedClick(0, 0) + reportedClick(session.columns - 1, session.rows - 1))
    }

    @Test
    fun middleButtonIsReportedAsButtonOne() {
        play(MOUSE_PRESS_REPORTING)
        press(point(1, 1), PointerButton.Tertiary)
        assertThat(written()).isEqualTo(sgr(MIDDLE_BUTTON, 1, 1))
    }

    @Test
    fun ctrlAndAltAddTheirFlagsToTheButtonCode() {
        play(MOUSE_PRESS_REPORTING)
        press(point(0, 0), ctrl = true)
        press(point(0, 0), alt = true)
        press(point(0, 0), ctrl = true, alt = true)
        assertThat(written()).isEqualTo(sgr(CTRL_FLAG, 0, 0) + sgr(META_FLAG, 0, 0) + sgr(CTRL_FLAG or META_FLAG, 0, 0))
    }

    @Test
    fun shiftPressInMouseModeSelectsInsteadOfReporting() {
        play("${ESC}[2J${ESC}[H$MOUSE_PRESS_REPORTING" + "hello brave")
        press(point(0, 0), shift = true)
        drag(point(4, 0))
        release(point(4, 0))
        assertThat(written()).isEmpty()
        assertThat(session.selection).isEqualTo(CellSelection(Cell(0, 0), Cell(4, 0)))
    }

    @Test
    fun dragIsReportedWithTheDragFlagWhenTheApplicationAskedForIt() {
        play(MOUSE_DRAG_REPORTING)
        press(point(1, 1))
        drag(point(3, 1))
        release(point(3, 1))
        assertThat(written()).isEqualTo(sgr(0, 1, 1) + sgr(DRAG_FLAG, 3, 1) + sgr(0, 3, 1, 'm'))
        assertThat(session.selection).isNull()
    }

    @Test
    fun shiftHeldDuringAReportedDragSuppressesThatDragReport() {
        play(MOUSE_DRAG_REPORTING)
        press(point(1, 1))
        drag(point(3, 1), shift = true)
        release(point(3, 1))
        assertThat(written()).isEqualTo(sgr(0, 1, 1) + sgr(0, 3, 1, 'm'))
    }

    @Test
    fun releaseOfAnUnreportedPressSendsNothing() {
        play("${ESC}[2J${ESC}[Hhello")
        click(2, 0)
        assertThat(written()).isEmpty()
    }

    @Test
    fun motionWithoutButtonsIsReportedOnlyInAnyMotionMode() {
        play(MOUSE_PRESS_REPORTING)
        pointer(PointerEventType.Move, point(2, 2))
        assertThat(written()).isEmpty()
        play(MOUSE_ANY_MOTION_REPORTING)
        pointer(PointerEventType.Move, point(6, 2))
        assertThat(written()).isEqualTo(sgr(DRAG_FLAG or 3, 6, 2))
    }

    @Test
    fun wheelIsReportedAsButtons64And65() {
        play(MOUSE_PRESS_REPORTING)
        wheel(point(4, 2), -1f)
        wheel(point(4, 2), 1f)
        assertThat(written()).isEqualTo(sgr(WHEEL_UP, 4, 2) + sgr(WHEEL_DOWN, 4, 2))
    }

    @Test
    fun wheelReportCarriesModifiersAndClampedCoordinates() {
        play(MOUSE_PRESS_REPORTING)
        wheel(Offset(WIDTH.toFloat() - 1, HEIGHT.toFloat() - 1), 1f, ctrl = true)
        assertThat(written()).isEqualTo(sgr(WHEEL_DOWN or CTRL_FLAG, session.columns - 1, session.rows - 1))
    }

    @Test
    fun fractionalWheelDeltaIsHeldBackUntilItAddsUpToALine() {
        play(MOUSE_PRESS_REPORTING)
        wheel(point(1, 1), 0.4f)
        wheel(point(1, 1), 0.4f)
        assertThat(written()).isEmpty()
        wheel(point(1, 1), 0.4f)
        assertThat(written()).isEqualTo(sgr(WHEEL_DOWN, 1, 1))
    }

    @Test
    fun wheelWithoutMouseModeScrollsTheScrollbackThreeLinesPerNotch() {
        play((1..80).joinToString("\r\n") { "row $it" })
        wheel(point(5, 5), -1f)
        assertThat(session.scrollTarget).isEqualTo(3f)
        wheel(point(5, 5), 1f)
        assertThat(session.scrollTarget).isZero()
        assertThat(written()).isEmpty()
    }

    @Test
    fun shiftWheelScrollsTheScrollbackEvenInMouseMode() {
        play(MOUSE_PRESS_REPORTING + (1..80).joinToString("\r\n") { "row $it" })
        wheel(point(5, 5), -1f, shift = true)
        assertThat(session.scrollTarget).isEqualTo(3f)
        assertThat(written()).isEmpty()
    }

    @Test
    fun scrollingPastTheScrollbackEndsIsClamped() {
        play((1..80).joinToString("\r\n") { "row $it" })
        wheel(point(5, 5), -1000f)
        assertThat(session.scrollTarget).isEqualTo(session.scrollbackLines.toFloat())
        wheel(point(5, 5), 1000f)
        assertThat(session.scrollTarget).isZero()
    }

    @Test
    fun aWheelEventWithoutDeltaChangesNothing() {
        play((1..80).joinToString("\r\n") { "row $it" })
        wheel(point(5, 5), 0f)
        assertThat(session.scrollTarget).isZero()
        assertThat(written()).isEmpty()
    }

    @Test
    fun selectionWhileScrolledBackAnchorsInScrollbackLines() {
        play((1..80).joinToString("\r\n") { "row $it" })
        session.scrollJumpTo(10f)
        frame()
        press(point(0, 0))
        drag(point(2, 0))
        release(point(2, 0))
        val selection = session.selection!!
        assertThat(selection.anchor).isEqualTo(Cell(0, -10))
        assertThat(selection.head).isEqualTo(Cell(2, -10))
    }
}
