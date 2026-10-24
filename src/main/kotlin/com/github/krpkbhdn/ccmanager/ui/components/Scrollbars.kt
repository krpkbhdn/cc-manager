package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val thickness = 6.dp
private val minimalThumb = 32.dp
private const val IDLE_ALPHA = 0.16f
private const val HOVER_ALPHA = 0.38f

@Composable
private fun thinStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = minimalThumb,
    thickness = thickness,
    shape = RoundedCornerShape(thickness),
    hoverDurationMillis = Motion.BASE,
    unhoverColor = colors.textPrimary.copy(alpha = IDLE_ALPHA),
    hoverColor = colors.textPrimary.copy(alpha = HOVER_ALPHA),
)

/** Thin vertical scrollbar docked to the end edge of the enclosing box. */
@Composable
fun BoxScope.ThinScrollbar(state: LazyListState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp), style = thinStyle())
}

@Composable
fun BoxScope.ThinScrollbar(state: LazyGridState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp), style = thinStyle())
}

@Composable
fun BoxScope.ThinScrollbar(state: ScrollState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp), style = thinStyle())
}

@Composable
fun BoxScope.ThinHorizontalScrollbar(state: ScrollState) {
    HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp), style = thinStyle())
}
