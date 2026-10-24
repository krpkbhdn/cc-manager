package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.notification.NotificationKind

/** Every colour the UI paints with; one instance per theme, read through [colors]. */
@Immutable
class AppColors(
    val dark: Boolean,
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceHover: Color,
    val surfaceSelected: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    val scrim: Color,
    val diffAdded: Color,
    val diffRemoved: Color,
    val diffHunk: Color,
    val surfaceRaised: Color,
    val borderStrong: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val terminal: Color,
    val highlight: Color,
    val shadow: Color,
    val diffAddedStrong: Color,
    val diffRemovedStrong: Color,
    val hoverOverlay: Color,
    val pressOverlay: Color,
) {
    fun status(status: AgentStatus): Color = when (status) {
        AgentStatus.OFFLINE -> neutral
        AgentStatus.IDLE -> success
        AgentStatus.WORKING -> warning
        AgentStatus.NEEDS_ACTION -> danger
    }

    fun notification(kind: NotificationKind): Color = when (kind) {
        NotificationKind.FINISHED -> success
        NotificationKind.NEEDS_ACTION -> danger
        NotificationKind.ERROR -> danger
        NotificationKind.INFO -> accent
    }
}

object Palette {
    val light = AppColors(
        dark = false,
        background = Color(0xFFFAF9F7),
        sidebar = Color(0xFFF3F1EE),
        surface = Color(0xFFFFFFFF),
        surfaceHover = Color(0xFFF1EFEB),
        surfaceSelected = Color(0xFFFBEBE4),
        border = Color(0x14201A14),
        textPrimary = Color(0xFF1F1B17),
        textSecondary = Color(0xFF5C554E),
        textTertiary = Color(0xFF6F6860),
        accent = Color(0xFFB04F2E),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF0F7A4F),
        warning = Color(0xFFA85F00),
        danger = Color(0xFFD02B48),
        neutral = Color(0xFF9B948B),
        scrim = Color(0x66120E0A),
        diffAdded = Color(0xFFE6F4EC),
        diffRemoved = Color(0xFFFCE8EB),
        diffHunk = Color(0xFFF4EEE9),
        surfaceRaised = Color(0xFFFFFFFF),
        borderStrong = Color(0x26201A14),
        accentHover = Color(0xFFBE5B39),
        accentPressed = Color(0xFF9A4224),
        accentSoft = Color(0x1FB04F2E),
        terminal = Color(0xFFFFFFFF),
        highlight = Color(0x66FFFFFF),
        shadow = Color(0x1F3B2A1A),
        diffAddedStrong = Color(0xFFBFE4CD),
        diffRemovedStrong = Color(0xFFF6C3CB),
        hoverOverlay = Color(0x0D201A14),
        pressOverlay = Color(0x17201A14),
    )

    val dark = AppColors(
        dark = true,
        background = Color(0xFF0F0F10),
        sidebar = Color(0xFF131314),
        surface = Color(0xFF1A1A1C),
        surfaceHover = Color(0xFF222224),
        surfaceSelected = Color(0xFF2A2220),
        border = Color(0x17FFFFFF),
        textPrimary = Color(0xFFF2F0ED),
        textSecondary = Color(0xFFA9A39C),
        textTertiary = Color(0xFF8A847D),
        accent = Color(0xFFD97757),
        onAccent = Color(0xFF1F130D),
        success = Color(0xFF3DD68C),
        warning = Color(0xFFF2B93B),
        danger = Color(0xFFFF5C7A),
        neutral = Color(0xFF6B665F),
        scrim = Color(0xB3050505),
        diffAdded = Color(0xFF14291E),
        diffRemoved = Color(0xFF2D171B),
        diffHunk = Color(0xFF1E1D22),
        surfaceRaised = Color(0xFF212124),
        borderStrong = Color(0x29FFFFFF),
        accentHover = Color(0xFFE48A6C),
        accentPressed = Color(0xFFC4623F),
        accentSoft = Color(0x26D97757),
        terminal = Color(0xFF0B0B0C),
        highlight = Color(0x14FFFFFF),
        shadow = Color(0x99000000),
        diffAddedStrong = Color(0xFF1F4A33),
        diffRemovedStrong = Color(0xFF5A2530),
        hoverOverlay = Color(0x0DFFFFFF),
        pressOverlay = Color(0x17FFFFFF),
    )
}

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp
}

/** Glyph edge lengths used across the UI. */
object IconSize {
    val small = 12.dp
    val medium = 14.dp
    val large = 16.dp
    val xlarge = 20.dp
}

object Radius {
    val xs = 4.dp
    val sm = 6.dp
    val md = 10.dp
    val lg = 14.dp
    val xl = 18.dp
    val pill = 100.dp
}

object Alpha {
    const val SOFT = 0.14f
    const val BORDER_TINT = 0.45f
    const val DISABLED = 0.45f
    const val PULSE_LOW = 0.4f
    const val HALO = 0.25f
    const val GLOW = 0.5f
    const val FAINT = 0.08f
}

val LocalAppColors = staticCompositionLocalOf { Palette.dark }

val colors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
