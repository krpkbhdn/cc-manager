package com.github.krpkbhdn.ccmanager.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Groups are project rows with a service directory under the data dir; members point to them via group_id. */
@Service
public class ProjectGroupService {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final ProjectService projects;
    private final GroupWorkspace workspace;

    public ProjectGroupService(JdbcClient jdbc, TransactionTemplate transaction, ProjectService projects,
                               GroupWorkspace workspace) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.projects = projects;
        this.workspace = workspace;
    }

    @EventListener(ApplicationReadyEvent.class)
    void sweepOrphanedWorkspaces() {
        workspace.sweep(jdbc.sql("SELECT path FROM project WHERE is_group = 1").query(String.class).list().stream().map(Path::of).toList());
    }

    /** @throws IllegalArgumentException with a user-readable message */
    public Project create(String name, List<Long> memberIds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Group name must not be empty");
        }
        var directory = workspace.create();
        Long groupId;
        try {
            groupId = transaction.execute(status -> {
                var keys = new GeneratedKeyHolder();
                jdbc.sql("INSERT INTO project (name, path, created_at, is_group) VALUES (?, ?, ?, 1)")
                        .param(name.strip()).param(directory.toString()).param(Instant.now().toString()).update(keys);
                var id = keys.getKey().longValue();
                assign(id, memberIds);
                return id;
            });
        } catch (RuntimeException e) {
            workspace.delete(directory);
            throw e;
        }
        projects.fireChanged();
        return projects.find(groupId).orElseThrow();
    }

    /** @throws IllegalArgumentException with a user-readable message */
    public Project setMembers(long groupId, List<Long> memberIds) {
        if (!projects.find(groupId).map(Project::group).orElse(false)) {
            throw new IllegalArgumentException("Not a group: " + groupId);
        }
        transaction.executeWithoutResult(status -> {
            jdbc.sql("UPDATE project SET group_id = NULL WHERE group_id = ?").param(groupId).update();
            assign(groupId, memberIds);
        });
        projects.fireChanged();
        return projects.find(groupId).orElseThrow();
    }

    public List<Project> members(long groupId) {
        return projects.list().stream().filter(p -> Long.valueOf(groupId).equals(p.groupId())).toList();
    }

    private void assign(long groupId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("A group needs at least one project");
        }
        for (var id : memberIds) {
            var member = projects.find(id).orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
            if (member.group()) {
                throw new IllegalArgumentException("A group cannot be a member of a group: " + member.name());
            }
            if (member.groupId() != null) {
                throw new IllegalArgumentException("Project is already in another group: " + member.name());
            }
            jdbc.sql("UPDATE project SET group_id = ? WHERE id = ?").param(groupId).param(id).update();
        }
    }
}
