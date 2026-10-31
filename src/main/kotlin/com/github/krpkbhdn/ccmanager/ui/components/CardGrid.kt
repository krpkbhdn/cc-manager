package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val cardMinWidth = 320.dp
private val gridPadding = 28.dp

/** Scrolling adaptive grid of cards with a thin scrollbar, shared by the overview and session screens. */
@Composable
fun CardGrid(modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    val grid = rememberLazyGridState()
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cardMinWidth),
            modifier = Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(horizontal = gridPadding, vertical = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            content = content,
        )
        ThinScrollbar(grid)
    }
}
