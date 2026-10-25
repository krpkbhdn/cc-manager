package com.github.krpkbhdn.ccmanager.ui.extensions.mcp


import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody

import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.extensions.description
import com.github.krpkbhdn.ccmanager.ui.extensions.label
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp
private const val JSON_EXAMPLE = "{\n  \"type\": \"http\",\n  \"url\": \"https://mcp.example.com/mcp\"\n}"

private enum class InputMode(val label: String) { FORM("Form"), JSON("Paste JSON") }

/** Collects a server as form fields or as pasted JSON and hands the JSON object to [McpState.add]. */
@Composable
fun AddServerDialog(context: ExtensionContext, state: McpState) {
    var mode by remember { mutableStateOf(InputMode.FORM) }
    var scope by remember { mutableStateOf(context.writableScopes.first()) }
    var transport by remember { mutableStateOf(McpTransport.STDIO) }
    val name = rememberTextFieldState()
    val command = rememberTextFieldState()
    val args = rememberTextFieldState()
    val url = rememberTextFieldState()
    val pasted = rememberTextFieldState()
    val pairs = remember { mutableStateListOf<PairRow>() }
    val form = ServerForm(transport, command.text.toString(), args.text.toString(), url.text.toString(), pairs.map { it.key.text.toString() to it.value.text.toString() })
    val taken = state.servers.filter { it.scope == scope }.map { it.name }
    val nameText = name.text.toString().trim()
    val nameError = if (nameText.isEmpty()) null else nameProblem(nameText, taken)
    val json = if (mode == InputMode.FORM) serverJson(form) else pasted.text.toString()
    val contentError = if (mode == InputMode.FORM) formProblem(form) else jsonProblem(json)
    val canAdd = nameText.isNotEmpty() && nameError == null && contentError == null && state.job == null
    val dismiss = { state.adding = false }
    val hasInput = { name.text.isNotEmpty() || command.text.isNotEmpty() || url.text.isNotEmpty() || pasted.text.isNotEmpty() || pairs.isNotEmpty() }
    ModalHost(dismiss, width = dialogWidth, hasInput = hasInput) {
        DialogBody("Add MCP server", Icons.Server, colors.accent, actions = { DialogActions("Add server", canAdd, false, { state.add(nameText, json, scope) }, dismiss) }) {
            SegmentedControl(InputMode.entries.map { Segment(it, it.label) }, mode, { mode = it })
            FormField("Name", error = nameError, hint = "How Claude refers to the server, for example github") {
                AppTextField(name, Modifier.fillMaxWidth(), placeholder = "server-name")
            }
            ScopeChoice(context, scope) { scope = it }
            if (mode == InputMode.FORM) {
                FormBody(transport, { transport = it }, command, args, url, pairs)
            } else {
                FormField("Server config", hint = "The object of one server, as in an .mcp.json file", error = contentError.takeIf { pasted.text.isNotBlank() }) {
                    AppTextArea(pasted, Modifier.fillMaxWidth(), placeholder = JSON_EXAMPLE, mono = true, minLines = 6, maxLines = 12)
                }
            }
        }
    }
}

@Composable
private fun ScopeChoice(context: ExtensionContext, scope: ExtensionScope, onSelect: (ExtensionScope) -> Unit) {
    FormField("Available to", hint = scope.description()) {
        if (context.writableScopes.size > 1) {
            SegmentedControl(context.writableScopes.map { Segment(it, it.label()) }, scope, onSelect)
        } else {
            ScopeBadge(scope)
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
    PairEditor(if (transport == McpTransport.STDIO) "Environment variables" else "Headers", if (transport == McpTransport.STDIO) "Add variable" else "Add header", pairs)
}

