package com.github.krpkbhdn.ccmanager.ui.proposal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class FormDraftTest {
    private val draft = FormDraft(
        formFields(
            JsonMapper.builder().build().readTree("""{"required":["name"],"properties":{"name":{"type":"string"},"on":{"type":"boolean","default":true}}}"""),
        ),
    )

    @Test
    fun acceptingWithAMissingRequiredFieldShowsTheErrorsAndSendsNothing() {
        assertThat(draft.showErrors).isFalse()

        assertThat(draft.accept()).isNull()
        assertThat(draft.showErrors).isTrue()
        assertThat(draft.errors).containsOnlyKeys("name")
    }

    @Test
    fun acceptingAValidFormSendsTheTypedContent() {
        draft.values["name"] = "Ada"

        assertThat(draft.accept()!!.content().toString()).isEqualTo("""{"name":"Ada","on":true}""")
    }
}
