package com.github.krpkbhdn.ccmanager.ui.proposal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class FormSchemaTest {
    private val schema = JsonMapper.builder().build().readTree(
        """
        {"required":["name","count"],"properties":{
          "name":{"type":"string","title":"Name","description":"Who"},
          "mode":{"type":"string","enum":["a","b"]},
          "count":{"type":"integer"},
          "ratio":{"type":"number"},
          "on":{"type":"boolean","default":true}}}
        """.trimIndent(),
    )
    private val fields = formFields(schema)

    @Test
    fun propertiesBecomeTypedFieldsWithLabelsAndRequiredFlags() {
        assertThat(fields.map { it.key to it.kind }).containsExactly(
            "name" to FieldKind.TEXT, "mode" to FieldKind.CHOICE, "count" to FieldKind.INTEGER, "ratio" to FieldKind.NUMBER, "on" to FieldKind.BOOLEAN,
        )
        assertThat(fields[0].label).isEqualTo("Name")
        assertThat(fields[0].help).isEqualTo("Who")
        assertThat(fields[1].label).isEqualTo("mode")
        assertThat(fields.filter { it.required }.map { it.key }).containsExactly("name", "count")
        assertThat(fields[1].options).containsExactly("a", "b")
        assertThat(fields[4].initial).isEqualTo("true")
    }

    @Test
    fun missingRequiredAndMalformedNumbersAreReported() {
        val errors = formErrors(fields, mapOf("name" to " ", "count" to "1.5", "ratio" to "abc"))

        assertThat(errors).containsOnlyKeys("name", "count", "ratio")
        assertThat(errors["name"]).isEqualTo("Required")
        assertThat(errors["count"]).isEqualTo("Enter a whole number")
    }

    @Test
    fun validValuesBuildTypedContentAndSkipBlankOptionalFields() {
        val values = mapOf("name" to "Ada", "count" to "3", "ratio" to "", "mode" to "b", "on" to "false")

        assertThat(formErrors(fields, values)).isEmpty()
        assertThat(formContent(fields, values).toString()).isEqualTo("""{"name":"Ada","mode":"b","count":3,"on":false}""")
    }

    @Test
    fun emptySchemaGivesNoFields() {
        assertThat(formFields(JsonMapper.builder().build().readTree("{}"))).isEmpty()
    }
}
