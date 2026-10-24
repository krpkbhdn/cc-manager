package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.notification.NotificationKind

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
        background = Color(0xFFF7F8FA),
        sidebar = Color(0xFFEFF1F5),
        surface = Color(0xFFFFFFFF),
        surfaceHover = Color(0xFFF0F2F6),
        surfaceSelected = Color(0xFFE4EAFB),
        border = Color(0xFFDFE2E8),
        textPrimary = Color(0xFF1B1F27),
        textSecondary = Color(0xFF5B6270),
        textTertiary = Color(0xFF8A91A0),
        accent = Color(0xFF3C6DF0),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF2DA44E),
        warning = Color(0xFFC98A0B),
        danger = Color(0xFFE5484D),
        neutral = Color(0xFF9AA1AF),
        scrim = Color(0x66101420),
        diffAdded = Color(0xFFE3F6E8),
        diffRemoved = Color(0xFFFDE6E7),
        diffHunk = Color(0xFFE8EEFC),
    )

    val dark = AppColors(
        dark = true,
        background = Color(0xFF14161B),
        sidebar = Color(0xFF191C22),
        surface = Color(0xFF1E2128),
        surfaceHover = Color(0xFF262A33),
        surfaceSelected = Color(0xFF263253),
        border = Color(0xFF2E323C),
        textPrimary = Color(0xFFE8EAF0),
        textSecondary = Color(0xFFA2A8B6),
        textTertiary = Color(0xFF6F7585),
        accent = Color(0xFF6B93FF),
        onAccent = Color(0xFF0B1020),
        success = Color(0xFF3FB950),
        warning = Color(0xFFD29922),
        danger = Color(0xFFF85149),
        neutral = Color(0xFF6A7080),
        scrim = Color(0x99000000),
        diffAdded = Color(0xFF16301F),
        diffRemoved = Color(0xFF3A1B1E),
        diffHunk = Color(0xFF1F2A44),
    )
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val sm = 6.dp
    val md = 10.dp
    val lg = 14.dp
    val pill = 100.dp
}

object Alpha {
    const val SOFT = 0.14f
    const val BORDER_TINT = 0.45f
    const val DISABLED = 0.45f
    const val PULSE_LOW = 0.4f
    const val HALO = 0.25f
}

val LocalAppColors = staticCompositionLocalOf { Palette.dark }

val colors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
