package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.team.RoleDraft
import com.github.krpkbhdn.ccmanager.team.TeamService
import java.nio.file.Files
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate

/** A real [TeamService] on a throwaway SQLite file, seeded by the migration like the app and given a second team for the list. */
fun previewTeamService(): TeamService {
    val file = Files.createTempFile("ccm-preview-teams", ".db").also { it.toFile().deleteOnExit() }
    val dataSource = DriverManagerDataSource("jdbc:sqlite:$file?journal_mode=WAL&busy_timeout=5000")
    Flyway.configure().dataSource(dataSource).load().migrate()
    return TeamService(JdbcClient.create(dataSource), TransactionTemplate(DataSourceTransactionManager(dataSource))).also { service ->
        service.create("Docs squad", listOf(
            RoleDraft("editor", "Leads the writing: outlines, assigns sections, reviews the final text.", "You edit and decide.", "sonnet", "high", null, true),
            RoleDraft("writer", "Use to draft or rewrite a section of documentation.", "You write clear docs.", "haiku", null, "Read, Edit, Write", false),
        ))
    }
}
