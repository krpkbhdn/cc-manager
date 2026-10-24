package com.github.krpkbhdn.ccmanager.ui.git.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogFilterTest {
    @Test
    fun `an empty filter asks for everything`() {
        val query = LogFilter().toQuery(skip = 0)

        assertThat(query.branch()).isNull()
        assertThat(query.author()).isNull()
        assertThat(query.text()).isNull()
        assertThat(query.path()).isNull()
        assertThat(query.limit()).isEqualTo(LOG_PAGE_SIZE)
        assertThat(LogFilter().isActive).isFalse()
    }

    @Test
    fun `blank fields are dropped and text is trimmed`() {
        val query = LogFilter(branch = "main", author = "  ", text = " fix ", path = " ").toQuery(skip = 400, limit = 50)

        assertThat(query.branch()).isEqualTo("main")
        assertThat(query.author()).isNull()
        assertThat(query.text()).isEqualTo("fix")
        assertThat(query.path()).isNull()
        assertThat(query.skip()).isEqualTo(400)
        assertThat(query.limit()).isEqualTo(50)
    }

    @Test
    fun `changing the branch or path keeps the other fields`() {
        val filter = LogFilter(author = "anna").forBranch("develop").forPath("src/A.java")

        assertThat(filter).isEqualTo(LogFilter("develop", "anna", "", "src/A.java"))
        assertThat(filter.isActive).isTrue()
        assertThat(filter.forBranch(null).forPath(null).copy(author = "")).isEqualTo(LogFilter())
    }

    @Test
    fun `the log view column breakpoints drop author before hash`() {
        assertThat(logColumnsFor(300f)).isEqualTo(LogColumns(date = false, author = false, hash = false))
        assertThat(logColumnsFor(500f)).isEqualTo(LogColumns(date = true, author = false, hash = false))
        assertThat(logColumnsFor(650f)).isEqualTo(LogColumns(date = true, author = true, hash = false))
        assertThat(logColumnsFor(900f)).isEqualTo(LogColumns(date = true, author = true, hash = true))
    }
}
