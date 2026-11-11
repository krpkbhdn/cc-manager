package com.github.krpkbhdn.ccmanager.author;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Remembers, per setup item, whether the author's item was installed or declined and in which version. */
@Service
public class AuthorDecisionService {

    private static final String UPSERT = "INSERT INTO author_item_decision (item_id, status, version) VALUES (?, ?, ?) "
            + "ON CONFLICT (item_id) DO UPDATE SET status = excluded.status, version = excluded.version, "
            + "decided_at = CURRENT_TIMESTAMP";

    private final JdbcClient jdbc;

    public AuthorDecisionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String itemId, AuthorChoice choice, int version) {
        jdbc.sql(UPSERT).param(itemId).param(choice.name()).param(version).update();
    }

    public Map<String, AuthorDecision> findAll() {
        Map<String, AuthorDecision> found = new LinkedHashMap<>();
        jdbc.sql("SELECT item_id, status, version FROM author_item_decision")
                .query((rs, row) -> new AuthorDecision(rs.getString("item_id"),
                        AuthorChoice.valueOf(rs.getString("status")), rs.getInt("version")))
                .list().forEach(decision -> found.put(decision.itemId(), decision));
        return found;
    }
}
