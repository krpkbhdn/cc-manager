package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.extensions.BusyLabel
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** What the install did, with a Sign in button for every MCP server that needs one. */
@Composable
fun AuthorReportPanel(report: AuthorReport, onClose: () -> Unit) {
    AuthorDialog("Author configuration", onClose) {
        report.lines.forEach { Label(it, color = colors.textSecondary, style = Type.small, maxLines = 3) }
        report.signIns.forEach { signIn ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(signIn.server, Modifier.weight(1f), style = Type.bodyMedium)
                if (signIn.mcp.signingIn == signIn.server) {
                    BusyLabel("Signing in")
                } else {
                    AppButton("Sign in", { signIn.mcp.signIn(signIn.server) }, icon = Icons.LogIn, compact = true, enabled = signIn.mcp.signingIn == null)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AppButton("Close", onClose, variant = ButtonVariant.PRIMARY) }
    }
}
