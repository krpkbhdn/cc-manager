package com.github.krpkbhdn.ccmanager.ui.proposal

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

private val mapper = JsonMapper.builder().build()

enum class FieldKind { TEXT, CHOICE, NUMBER, INTEGER, BOOLEAN }

/** One input of an MCP form, derived from a property of the requested JSON schema. */
data class FormField(
    val key: String,
    val label: String,
    val help: String,
    val required: Boolean,
    val kind: FieldKind,
    val options: List<String> = emptyList(),
    val initial: String = "",
)

/** The fields of a flat object schema; types the form cannot show become text fields. */
fun formFields(schema: JsonNode): List<FormField> {
    val required = schema.path("required").values().map { node -> node.asString("") }.toSet()
    return schema.path("properties").properties().map { (key, property) ->
        val options = property.path("enum").values().map { node -> node.asString("") }
        val kind = when {
            options.isNotEmpty() -> FieldKind.CHOICE
            property.path("type").asString("") == "number" -> FieldKind.NUMBER
            property.path("type").asString("") == "integer" -> FieldKind.INTEGER
            property.path("type").asString("") == "boolean" -> FieldKind.BOOLEAN
            else -> FieldKind.TEXT
        }
        val initial = property.path("default").asString("").ifEmpty { if (kind == FieldKind.BOOLEAN) "false" else "" }
        FormField(key, property.path("title").asString(key), property.path("description").asString(""), key in required, kind, options, initial)
    }
}

/** Error text by field key for every value the user has to fix; empty when the form can be sent. */
fun formErrors(fields: List<FormField>, values: Map<String, String>): Map<String, String> =
    fields.mapNotNull { field ->
        val value = values[field.key].orEmpty().trim()
        val error = when {
            value.isEmpty() -> "Required".takeIf { field.required && field.kind != FieldKind.BOOLEAN }
            field.kind == FieldKind.NUMBER -> "Enter a number".takeIf { value.toDoubleOrNull()?.isFinite() != true }
            field.kind == FieldKind.INTEGER -> "Enter a whole number".takeIf { value.toLongOrNull() == null }
            else -> null
        }
        error?.let { field.key to it }
    }.toMap()

/** The accepted content, typed per field; blank optional fields are left out. Call only when [formErrors] is empty. */
fun formContent(fields: List<FormField>, values: Map<String, String>): ObjectNode {
    val content = mapper.createObjectNode()
    for (field in fields) {
        val value = values[field.key].orEmpty().trim()
        when {
            field.kind == FieldKind.BOOLEAN -> content.put(field.key, value.toBoolean())
            value.isEmpty() -> Unit
            field.kind == FieldKind.INTEGER -> content.put(field.key, value.toLong())
            field.kind == FieldKind.NUMBER -> content.put(field.key, value.toDouble())
            else -> content.put(field.key, value)
        }
    }
    return content
}
