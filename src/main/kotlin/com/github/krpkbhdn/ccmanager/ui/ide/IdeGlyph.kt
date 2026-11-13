package com.github.krpkbhdn.ccmanager.ui.ide

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** An IDE's own icon, or the generic code glyph while it is not read or not available. */
@Composable
fun IdeGlyph(icon: ImageBitmap?) {
    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(IconSize.large))
    else Glyph(Icons.Code, colors.textSecondary)
}
