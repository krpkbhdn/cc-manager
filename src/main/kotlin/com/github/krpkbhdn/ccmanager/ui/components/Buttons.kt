package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val buttonHeight = 32.dp
private val compactHeight = 28.dp
private val iconSize = 15.dp
private val iconButtonSize = 28.dp
private val glowElevation = 6.dp
private const val PRESS_SCALE = 0.97f
private const val GLOW_ALPHA = 0.35f

enum class ButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

private class ButtonLook(val fill: Brush?, val flat: Color, val hover: Color?, val text: Color, val border: Color, val glow: Boolean)

@Composable
private fun lookOf(variant: ButtonVariant, enabled: Boolean): ButtonLook = when (variant) {
    ButtonVariant.PRIMARY -> ButtonLook(
        fill = Brush.verticalGradient(listOf(colors.accentHover, colors.accent)),
        flat = colors.accent, hover = colors.accentHover, text = colors.onAccent, border = colors.accentPressed.copy(alpha = Alpha.BORDER_TINT), glow = enabled,
    )
    ButtonVariant.SECONDARY -> ButtonLook(null, colors.surfaceRaised, colors.surfaceHover, colors.textPrimary, colors.borderStrong, false)
    ButtonVariant.GHOST -> ButtonLook(null, Color.Transparent, null, colors.textSecondary, Color.Transparent, false)
    ButtonVariant.DANGER -> ButtonLook(null, colors.danger, null, if (colors.dark) colors.onAccent else Color.White, Color.Transparent, false)
}

/** Text button with optional leading [icon]; [loading] swaps the icon for a spinner and blocks clicks. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.SECONDARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    tooltip: String? = null,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.md),
) {
    val look = lookOf(variant, enabled)
    val glow = if (look.glow) Modifier.shadow(glowElevation, shape, ambientColor = colors.accent.copy(alpha = GLOW_ALPHA), spotColor = colors.accent.copy(alpha = GLOW_ALPHA)) else Modifier
    WithTooltip(tooltip) {
        Pressable(
            onClick = onClick,
            modifier = modifier.height(if (compact) compactHeight else buttonHeight).alpha(if (enabled) 1f else Alpha.DISABLED).then(glow),
            shape = shape,
            base = look.flat,
            hover = look.hover,
            enabled = enabled && !loading,
            pressScale = PRESS_SCALE,
        ) {
            Row(
                Modifier.then(look.fill?.let { Modifier.background(it) } ?: Modifier).then(if (look.border != Color.Transparent) Modifier.border(1.dp, look.border, shape) else Modifier)
                    .height(if (compact) compactHeight else buttonHeight)
                    .padding(horizontal = if (compact) Spacing.md else Spacing.lg - 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm - 1.dp),
            ) {
                when {
                    loading -> Spinner(size = iconSize, tint = look.text)
                    icon != null -> Glyph(icon, look.text, size = iconSize)
                }
                Label(text, color = look.text, style = Type.bodyMedium)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false) =
    AppButton(text, onClick, modifier, ButtonVariant.PRIMARY, icon, enabled, loading)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tooltip: String? = null,
) = AppButton(text, onClick, modifier, ButtonVariant.SECONDARY, icon, enabled, tooltip = tooltip)

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) =
    AppButton(text, onClick, modifier, ButtonVariant.GHOST, icon, enabled, compact = true)

/** Square icon-only button; [tooltip] doubles as the accessible label and [hint] shows the shortcut. */
@Composable
fun IconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    hint: String? = null,
    tint: Color = colors.textSecondary,
    enabled: Boolean = true,
    active: Boolean = false,
    size: Dp = iconButtonSize,
) {
    val shape = RoundedCornerShape(Radius.sm + 1.dp)
    val glyph by animateColorAsState(if (active) colors.accent else tint, Motion.fast(), label = "iconTint")
    WithTooltip(tooltip, hint = hint) {
        Pressable(
            onClick,
            modifier.size(size).alpha(if (enabled) 1f else Alpha.DISABLED),
            shape = shape,
            base = if (active) colors.accentSoft else Color.Transparent,
            enabled = enabled,
        ) { Glyph(icon, glyph, Modifier.align(Alignment.Center)) }
    }
}

/** Primary action with a chevron segment that opens [menu] entries. */
@Composable
fun SplitButton(text: String, icon: ImageVector, onClick: () -> Unit, menu: List<MenuEntry>, modifier: Modifier = Modifier, loading: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppButton(text, onClick, variant = ButtonVariant.PRIMARY, icon = icon, loading = loading, shape = RoundedCornerShape(topStart = Radius.md, bottomStart = Radius.md))
        Box(Modifier.width(1.dp).height(buttonHeight).background(colors.accentPressed.copy(alpha = Alpha.GLOW)))
        Box {
            Pressable(
                onClick = { expanded = true },
                modifier = Modifier.height(buttonHeight).width(buttonHeight - 4.dp),
                shape = RoundedCornerShape(topEnd = Radius.md, bottomEnd = Radius.md),
                base = colors.accent,
                hover = colors.accentHover,
                enabled = !loading,
            ) { Glyph(Icons.ChevronDown, colors.onAccent, Modifier.align(Alignment.Center), size = iconSize) }
            if (expanded) PopupMenu(menu, onDismiss = { expanded = false }, alignEnd = true)
        }
    }
}
