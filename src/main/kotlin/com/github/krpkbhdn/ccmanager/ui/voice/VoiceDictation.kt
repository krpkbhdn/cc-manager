package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.input.TextFieldBuffer
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HOLD_THRESHOLD_MILLIS = 350L
private const val FAILURE_SHOWN_MILLIS = 6000L
private val LINE_BREAKS = Regex("""\s*\R\s*""")

/**
 * Hold-Space dictation into one [TextFieldState]. The first Space types a space as usual; if Space is still down after [HOLD_THRESHOLD_MILLIS]
 * the spaces that hold typed are removed and speech is inserted at the caret, coloured until Space is released and the session has flushed.
 * Everything here runs on the UI thread; the recogniser calls back on its own thread and [scope] brings the results over.
 */
class VoiceDictation internal constructor(
    private val state: TextFieldState,
    private val scope: CoroutineScope,
    private val usable: () -> Boolean,
    private val start: (DictationListener) -> DictationSession,
    private val fail: (String) -> Unit,
    private val singleLine: Boolean = false,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    var active by mutableStateOf(false)
        private set
    var status by mutableStateOf<DictationStatus>(DictationStatus.Idle)
        private set

    /** Loudness of the latest audio block, 0 to 1, while listening. */
    var level by mutableStateOf(0f)
        private set
    internal val listening: Boolean get() = run?.listening == true
    private var dictated by mutableStateOf<IntRange?>(null)
    private var spaceDown = false
    private var armed = false
    private var holdConsumed = false
    private var holdTextLength = 0
    private var pending: Job? = null
    private var run: Run? = null

    internal fun outputTransformation(color: Color) = OutputTransformation {
        dictated?.let { highlight(it, color) }
    }

    private fun TextFieldBuffer.highlight(range: IntRange, color: Color) {
        val from = range.first.coerceAtMost(length)
        val to = (range.last + 1).coerceAtMost(length)
        if (from < to) addStyle(SpanStyle(color = color), from, to)
    }

    /** Returns true when the event was used up; plain Space and everything else pass through until a hold activates; from then to Space release every key is used up. */
    fun onKeyEvent(event: KeyEvent): Boolean = when {
        event.key == Key.Spacebar -> onSpace(event)
        event.key == Key.Escape -> stopByEscape()
        holdConsumed || run?.let { !it.released.isCompleted } == true -> true
        else -> {
            if (event.type == KeyEventType.KeyDown) disarm()
            false
        }
    }

    private fun stopByEscape(): Boolean {
        disarm()
        val current = run ?: return false
        val stopping = !current.released.isCompleted
        release()
        return stopping
    }

    /** Forgets a hold in progress and stops a running dictation: a field without focus gets no key events to finish them. */
    fun lostFocus() {
        disarm()
        spaceDown = false
        holdConsumed = false
        release()
    }

    private fun onSpace(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp) return onSpaceUp()
        val modified = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed
        return when {
            holdConsumed -> true
            spaceDown -> {
                false
            }
            modified || !usable() -> false
            else -> {
                spaceDown = true
                armed = true
                holdTextLength = state.text.length - state.selection.length
                pending = scope.launch { delay(HOLD_THRESHOLD_MILLIS); activate() }
                false
            }
        }
    }

    private fun onSpaceUp(): Boolean {
        val consumed = holdConsumed
        spaceDown = false
        holdConsumed = false
        disarm()
        release()
        return consumed
    }

    private fun disarm() {
        armed = false
        pending?.cancel()
    }

    private fun activate() {
        if (!armed || run != null) return
        armed = false
        holdConsumed = true
        val caret = state.selection.min
        val typed = (state.text.length - holdTextLength).coerceAtLeast(0)
        val removable = DictationEdit.spacesBefore(state.text.toString(), caret, typed)
        state.edit { delete(caret - removable, caret) }
        begin(DictationEdit(caret - removable))
    }

    private fun begin(edit: DictationEdit) {
        val current = Run(edit, state.text.toString())
        run = current
        active = true
        status = DictationStatus.Initializing
        level = 0f
        scope.launch(io) { listen(current) }
    }

    private suspend fun listen(current: Run) {
        val session = try {
            withContext(NonCancellable) { start(listenerOf(current)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ui { failed(current, e.message ?: e.toString()); finish(current) }
            return
        }
        ui { markListening(current) }
        try {
            current.released.await()
        } finally {
            withContext(NonCancellable) {
                session.close()
                ui { finish(current) }
            }
        }
    }

    /** Stops listening; the session flushes, then the colour goes and the text stays. */
    fun release() {
        run?.released?.complete(Unit)
    }

    private fun listenerOf(current: Run) = object : DictationListener {
        override fun onPartial(text: String) = ui { apply(current, text, commit = false) }

        override fun onFinal(text: String) = ui { apply(current, text, commit = true) }

        override fun onError(error: Throwable) = ui { failed(current, error.message ?: error.toString()) }

        override fun onLevel(level: Double) = ui { if (run === current && !current.released.isCompleted) this@VoiceDictation.level = level.toFloat() }
    }

    private fun apply(current: Run, utterance: String, commit: Boolean) {
        if (run !== current || current.dead) return
        val text = state.text.toString()
        if (text != current.expectedText) {
            dictated = null
            release()
            return
        }
        val applied = current.edit.apply(text, if (singleLine) utterance.replace(LINE_BREAKS, " ") else utterance, commit)
        state.edit {
            replace(applied.replaceFrom.coerceAtMost(length), applied.replaceTo.coerceAtMost(length), applied.inserted)
            selection = TextRange(applied.caret.coerceAtMost(length))
        }
        current.expectedText = state.text.toString()
        dictated = applied.dictated
    }

    private fun failed(current: Run, message: String) {
        if (run !== current || current.dead) return
        current.dead = true
        dictated = null
        showFailure(message)
        current.released.complete(Unit)
        fail(message)
    }

    private fun markListening(current: Run) {
        current.listening = true
        if (run === current && !current.dead && !current.released.isCompleted) status = DictationStatus.Listening
    }

    private fun showFailure(message: String) {
        val failure = DictationStatus.Failed(message)
        status = failure
        scope.launch {
            delay(FAILURE_SHOWN_MILLIS)
            if (status === failure) status = DictationStatus.Idle
        }
    }

    private fun finish(current: Run) {
        if (run !== current) return
        run = null
        dictated = null
        active = false
        level = 0f
        if (status !is DictationStatus.Failed) status = DictationStatus.Idle
    }

    private fun ui(action: () -> Unit) {
        scope.launch { action() }
    }

    private class Run(val edit: DictationEdit, var expectedText: String) {
        val released = CompletableDeferred<Unit>()
        var dead = false
        @Volatile var listening = false
    }
}

@Composable
fun rememberVoiceDictation(state: TextFieldState, voice: VoiceSettingsState?, singleLine: Boolean = false): VoiceDictation? {
    if (voice == null) return null
    val scope = rememberCoroutineScope()
    val dictation = remember(state, voice) { VoiceDictation(state, scope, { voice.dictationUsable }, voice::startDictation, voice::dictationFailed, singleLine) }
    DisposableEffect(dictation) { onDispose { dictation.release() } }
    return dictation
}
