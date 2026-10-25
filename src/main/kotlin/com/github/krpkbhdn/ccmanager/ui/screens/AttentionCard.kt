package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Duration
import java.time.Instant

private val accentBarWidth = 4.dp
private const val ASK_LINES = 3

/** Large actionable card for a session that waits for the user: what is asked and a jump to its terminal. */
@Composable
fun AttentionCard(projectName: String, session: SessionSnapshot, now: Instant, onOpen: () -> Unit, modifier: Modifier = Modifier, proposalHeadline: String? = null) {
    val tint = colors.danger
    val shape = RoundedCornerShape(Radius.xl)
    val waiting = session.lastEventAt?.let { "Waiting ${formatElapsed(Duration.between(it, now))}" }
    Pressable(
        onOpen,
        modifier.fillMaxWidth().shadow(14.dp, shape, ambientColor = tint.copy(alpha = Alpha.HALO), spotColor = tint.copy(alpha = Alpha.HALO))
            .border(1.dp, tint.copy(alpha = Alpha.BORDER_TINT), shape),
        shape = shape,
        base = colors.surface,
        hover = colors.surfaceHover,
    ) {
        Row(Modifier.height(IntrinsicSize.Min).background(Brush.horizontalGradient(listOf(tint.copy(alpha = Alpha.SOFT), tint.copy(alpha = 0f))))) {
            Box(Modifier.width(accentBarWidth).fillMaxHeight().background(tint))
            Row(
                Modifier.padding(Spacing.lg + 2.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                ProjectAvatar(projectName, size = 44.dp, status = session.status, cutout = colors.surface)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Label(projectName, style = Type.heading)
                        Label(sessionTitle(session), color = colors.textSecondary, style = Type.body, tooltipWhenTruncated = true)
                    }
                    Label(proposalHeadline ?: session.activity ?: "Waiting for your decision", color = colors.textPrimary, style = Type.subtitle, maxLines = ASK_LINES)
                    waiting?.let { Label(it, color = tint, style = Type.numeric) }
                }
                if (proposalHeadline != null) AppButton("Answer", onOpen, variant = ButtonVariant.PRIMARY, icon = Icons.Message) else AppButton("Jump to terminal", onOpen, variant = ButtonVariant.PRIMARY, icon = Icons.Terminal)
            }
        }
    }
}
