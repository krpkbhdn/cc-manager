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
    val optionLabels: List<String> = options,
    val initial: String = "",
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
)

/** The fields of a flat object schema; types the form cannot show become text fields. */
fun formFields(schema: JsonNode): List<FormField> {
    val required = schema.path("required").values().map { node -> node.asString("") }.toSet()
    return schema.path("properties").properties().map { (key, property) ->
        val choices = choicesOf(property)
        val kind = when {
            choices.isNotEmpty() -> FieldKind.CHOICE
            property.path("type").asString("") == "number" -> FieldKind.NUMBER
            property.path("type").asString("") == "integer" -> FieldKind.INTEGER
            property.path("type").asString("") == "boolean" -> FieldKind.BOOLEAN
            else -> FieldKind.TEXT
        }
        val initial = property.path("default").asString("").ifEmpty { if (kind == FieldKind.BOOLEAN) "false" else "" }
        FormField(
            key, property.path("title").asString(key), property.path("description").asString(""), key in required, kind,
            choices.map { it.first }, choices.map { it.second }, initial,
            property.path("minimum").asDouble(Double.NaN).takeUnless { it.isNaN() }, property.path("maximum").asDouble(Double.NaN).takeUnless { it.isNaN() },
            property.path("minLength").asInt(-1).takeIf { it >= 0 }, property.path("maxLength").asInt(-1).takeIf { it >= 0 },
        )
    }
}

/** Value to label pairs of an `enum` or a `oneOf` of `{const, title}`. */
private fun choicesOf(property: JsonNode): List<Pair<String, String>> {
    val enumValues = property.path("enum").values().map { node -> node.asString("") }
    if (enumValues.isNotEmpty()) return enumValues.map { it to it }
    return property.path("oneOf").values().map { option ->
        val value = option.path("const").asString("")
        value to option.path("title").asString(value)
    }.filter { it.first.isNotEmpty() }
}

// shortcut: `format` (email, uri, date) is not validated here, the server rejects bad values; upgrade if users hit it.
/** Error text by field key for every value the user has to fix; empty when the form can be sent. */
fun formErrors(fields: List<FormField>, values: Map<String, String>): Map<String, String> =
    fields.mapNotNull { field ->
        val value = values[field.key].orEmpty().trim()
        val error = when {
            value.isEmpty() -> "Required".takeIf { field.required && field.kind != FieldKind.BOOLEAN }
            field.kind == FieldKind.NUMBER -> numberError(field, value.toDoubleOrNull()?.takeIf { it.isFinite() }, "Enter a number")
            field.kind == FieldKind.INTEGER -> numberError(field, value.toLongOrNull()?.toDouble(), "Enter a whole number")
            field.kind == FieldKind.TEXT -> lengthError(field, value.length)
            else -> null
        }
        error?.let { field.key to it }
    }.toMap()

private fun numberError(field: FormField, number: Double?, malformed: String): String? = when {
    number == null -> malformed
    field.minimum != null && number < field.minimum -> "Must be at least ${field.minimum.toBigDecimal().stripTrailingZeros().toPlainString()}"
    field.maximum != null && number > field.maximum -> "Must be at most ${field.maximum.toBigDecimal().stripTrailingZeros().toPlainString()}"
    else -> null
}

private fun lengthError(field: FormField, length: Int): String? = when {
    field.minLength != null && length < field.minLength -> "Must be at least ${field.minLength} characters"
    field.maxLength != null && length > field.maxLength -> "Must be at most ${field.maxLength} characters"
    else -> null
}

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
