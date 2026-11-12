package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Whole pixels without a fraction, otherwise one decimal. */
internal fun formatPx(value: Double): String = if (value == Math.rint(value)) value.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, value)

/** Margin, border, padding and content of the element as nested labelled boxes, like the browser's own inspector. */
@Composable
fun BoxModelDiagram(box: BoxModel, modifier: Modifier = Modifier) {
    Layer("margin", box.margin, colors.warning, modifier) {
        Layer("border", box.border, colors.neutral) {
            Layer("padding", box.padding, colors.success) {
                Box(Modifier.fillMaxWidth().background(colors.accent.copy(alpha = Alpha.SOFT), RoundedCornerShape(Radius.xs)).padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
                    Label("${formatPx(box.contentW)} x ${formatPx(box.contentH)}", style = Type.numericCaption, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun Layer(name: String, edges: BoxEdges, tint: Color, modifier: Modifier = Modifier, inner: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier.fillMaxWidth().background(tint.copy(alpha = Alpha.SOFT), shape).border(1.dp, tint.copy(alpha = Alpha.BORDER_TINT), shape).padding(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Label(name, color = tint, style = Type.caption)
        Value(edges.t, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Value(edges.l, Modifier.padding(start = Spacing.xs))
            Box(Modifier.weight(1f)) { inner() }
            Value(edges.r, Modifier.padding(end = Spacing.xs))
        }
        Value(edges.b, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Value(value: Double, modifier: Modifier) =
    Label(formatPx(value), modifier, color = colors.textSecondary, style = Type.numericCaption, align = TextAlign.Center)
