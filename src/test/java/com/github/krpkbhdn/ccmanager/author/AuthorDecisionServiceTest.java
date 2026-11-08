package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthorDecisionServiceTest {

    @TempDir
    Path temp;

    AuthorDecisionService service;
    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        service = new AuthorDecisionService(JdbcClient.create(dataSource));
    }

    @Test
    void unknownItemHasNoDecision() {
        assertThat(service.findAll()).doesNotContainKey("plugin.context7");
    }

    @Test
    void recordStoresTheDecision() {
        service.record("skill.jscpd", AuthorChoice.DECLINED, 1);

        assertThat(service.findAll().get("skill.jscpd")).isEqualTo(new AuthorDecision("skill.jscpd", AuthorChoice.DECLINED, 1));
    }

    @Test
    void installingAfterDecliningOverwritesTheDecline() {
        service.record("plugin.context7", AuthorChoice.DECLINED, 1);
        service.record("plugin.context7", AuthorChoice.INSTALLED, 2);

        assertThat(service.findAll().get("plugin.context7")).isEqualTo(new AuthorDecision("plugin.context7", AuthorChoice.INSTALLED, 2));
    }
}
