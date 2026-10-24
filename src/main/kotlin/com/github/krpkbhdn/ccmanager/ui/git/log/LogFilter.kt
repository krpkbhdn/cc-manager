package com.github.krpkbhdn.ccmanager.ui.git.log

import com.github.krpkbhdn.ccmanager.git.GitLog

const val LOG_PAGE_SIZE = 200

/** What the filter bar edits; blank fields mean "no filter" and [branch] null means all refs. */
data class LogFilter(val branch: String? = null, val author: String = "", val text: String = "", val path: String? = null) {
    val isActive: Boolean get() = this != LogFilter()

    fun toQuery(skip: Int, limit: Int = LOG_PAGE_SIZE): GitLog.Query =
        GitLog.Query(branch, author.trim().ifEmpty { null }, text.trim().ifEmpty { null }, path?.trim()?.ifEmpty { null }, skip, limit)

    fun forBranch(name: String?): LogFilter = copy(branch = name)

    fun forPath(value: String?): LogFilter = copy(path = value)
}
