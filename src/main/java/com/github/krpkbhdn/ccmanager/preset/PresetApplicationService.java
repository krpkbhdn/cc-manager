package com.github.krpkbhdn.ccmanager.preset;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Which preset revision each project has applied, and whether the project declined the preset offer. */
@Service
public class PresetApplicationService {

    private final JdbcClient jdbc;

    public PresetApplicationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void markApplied(long projectId, long presetId, int revision) {
        jdbc.sql("INSERT INTO project_preset_application (project_id, preset_id, revision) VALUES (?, ?, ?) "
                        + "ON CONFLICT (project_id, preset_id) DO UPDATE SET revision = excluded.revision, applied_at = CURRENT_TIMESTAMP")
                .param(projectId).param(presetId).param(revision).update();
    }

    /** @return applied revision per preset id */
    public Map<Long, Integer> applications(long projectId) {
        var result = new HashMap<Long, Integer>();
        jdbc.sql("SELECT preset_id, revision FROM project_preset_application WHERE project_id = ?").param(projectId)
                .query(rs -> {
                    result.put(rs.getLong("preset_id"), rs.getInt("revision"));
                });
        return Map.copyOf(result);
    }

    /** @return ids of applied presets whose current revision is newer than the applied one */
    public Set<Long> outdated(long projectId) {
        return jdbc.sql("SELECT a.preset_id FROM project_preset_application a JOIN project_preset p ON p.id = a.preset_id "
                        + "WHERE a.project_id = ? AND p.deleted = 0 AND p.revision > a.revision").param(projectId)
                .query(Long.class).list().stream().collect(Collectors.toUnmodifiableSet());
    }

    public void declineOffer(long projectId) {
        jdbc.sql("INSERT OR IGNORE INTO project_preset_offer_declined (project_id) VALUES (?)").param(projectId).update();
    }

    public boolean offerDeclined(long projectId) {
        return jdbc.sql("SELECT COUNT(*) FROM project_preset_offer_declined WHERE project_id = ?").param(projectId).query(Long.class).single() > 0;
    }
}
