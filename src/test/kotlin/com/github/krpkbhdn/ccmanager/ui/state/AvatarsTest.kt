package com.github.krpkbhdn.ccmanager.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AvatarsTest {
    @Test
    fun `same name always maps to the same gradient regardless of case and spaces`() {
        assertThat(avatarIndex("cc-manager")).isEqualTo(avatarIndex("  CC-Manager "))
    }

    @Test
    fun `index stays inside the palette for any name`() {
        listOf("", "a", "billing-api", "Ünïcode проект", "x".repeat(500)).forEach {
            assertThat(avatarIndex(it)).isBetween(0, avatarGradients.lastIndex)
        }
    }

    @Test
    fun `different names spread over several gradients`() {
        val names = listOf("cc-manager", "billing-api", "docs-site", "infra-terraform", "demo-web", "notes", "ml-pipeline", "mobile-ios")
        assertThat(names.map(::avatarIndex).toSet().size).isGreaterThan(3)
    }

    @Test
    fun `initials come from the first letters of the first two words`() {
        assertThat(avatarInitials("cc-manager")).isEqualTo("CM")
        assertThat(avatarInitials("billing api service")).isEqualTo("BA")
        assertThat(avatarInitials("docs_site")).isEqualTo("DS")
    }

    @Test
    fun `a single word gives a capitalised pair of letters`() {
        assertThat(avatarInitials("notes")).isEqualTo("No")
        assertThat(avatarInitials("x")).isEqualTo("X")
    }

    @Test
    fun `blank names fall back to a question mark`() {
        assertThat(avatarInitials("   ")).isEqualTo("?")
    }
}
