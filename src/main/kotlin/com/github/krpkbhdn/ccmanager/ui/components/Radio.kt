package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val radioSize = 16.dp
private val radioDot = 8.dp

/** Indicator of a single-choice row; the row around it handles the click. */
@Composable
fun RadioMark(on: Boolean, modifier: Modifier = Modifier) {
    val ring = if (on) colors.accent else colors.borderStrong
    Box(modifier.size(radioSize).border(1.5.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(radioDot).background(colors.accent, CircleShape))
    }
}
