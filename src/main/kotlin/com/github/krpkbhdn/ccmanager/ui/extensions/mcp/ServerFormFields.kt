package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

private const val JSON_EXAMPLE = "{\n  \"type\": \"http\",\n  \"url\": \"https://mcp.example.com/mcp\"\n}"

enum class ServerInputMode(val label: String) { FORM("Form"), JSON("Paste JSON") }

/** The server config a dialog collects, as form fields or as pasted JSON; [json] is the server object either way. */
class ServerFormState(initialJson: String? = null) {
    var mode by mutableStateOf(if (initialJson == null) ServerInputMode.FORM else ServerInputMode.JSON)
    var transport by mutableStateOf(McpTransport.STDIO)
    val command = TextFieldState()
    val args = TextFieldState()
    val url = TextFieldState()
    val pasted = TextFieldState(initialJson ?: "")
    val pairs = mutableStateListOf<PairRow>()

    private val form: ServerForm
        get() = ServerForm(transport, command.text.toString(), args.text.toString(), url.text.toString(), pairs.map { it.key.text.toString() to it.value.text.toString() })

    val json: String get() = if (mode == ServerInputMode.FORM) serverJson(form) else pasted.text.toString()
    val problem: String? get() = if (mode == ServerInputMode.FORM) formProblem(form) else jsonProblem(json)
    val hasInput: Boolean get() = command.text.isNotEmpty() || url.text.isNotEmpty() || pasted.text.isNotEmpty() || pairs.isNotEmpty()
}

/** Mode switch, then [beforeBody] (name, scope), then the transport form or the JSON box. */
@Composable
fun ServerConfigFields(state: ServerFormState, pairHint: String? = null, beforeBody: @Composable () -> Unit = {}) {
    SegmentedControl(ServerInputMode.entries.map { Segment(it, it.label) }, state.mode, { state.mode = it })
    beforeBody()
    if (state.mode == ServerInputMode.FORM) {
        FormBody(state.transport, { state.transport = it }, state.command, state.args, state.url, state.pairs, pairHint)
    } else {
        FormField("Server config", hint = "The object of one server, as in an .mcp.json file", error = state.problem.takeIf { state.pasted.text.isNotBlank() }) {
            AppTextArea(state.pasted, Modifier.fillMaxWidth(), placeholder = JSON_EXAMPLE, mono = true, minLines = 6, maxLines = 12)
        }
    }
}

@Composable
private fun FormBody(
    transport: McpTransport,
    onTransport: (McpTransport) -> Unit,
    command: TextFieldState,
    args: TextFieldState,
    url: TextFieldState,
    pairs: SnapshotStateList<PairRow>,
    pairHint: String?,
) {
    FormField("Transport", hint = when (transport) {
        McpTransport.STDIO -> "Claude starts the server as a program on this computer"
        McpTransport.HTTP -> "Claude connects to a server over HTTP"
        McpTransport.SSE -> "Claude connects to a server over server-sent events (older remote servers)"
    }) {
        SegmentedControl(McpTransport.entries.map { Segment(it, it.label, icon = it.icon) }, transport, onTransport)
    }
    if (transport == McpTransport.STDIO) {
        FormField("Command") { AppTextField(command, Modifier.fillMaxWidth(), placeholder = "npx", mono = true) }
        FormField("Arguments", hint = "Separated by spaces; quote an argument that contains spaces") {
            AppTextField(args, Modifier.fillMaxWidth(), placeholder = "-y @modelcontextprotocol/server-github", mono = true)
        }
    } else {
        FormField("URL") { AppTextField(url, Modifier.fillMaxWidth(), placeholder = "https://mcp.example.com/mcp", mono = true, icon = Icons.Link) }
    }
    PairEditor(if (transport == McpTransport.STDIO) "Environment variables" else "Headers", if (transport == McpTransport.STDIO) "Add variable" else "Add header", pairs, emptyHint = pairHint ?: SECRET_HINT)
}
