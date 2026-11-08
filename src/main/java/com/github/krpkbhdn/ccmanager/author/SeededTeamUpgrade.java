package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.author.install.TeamsInstaller;
import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Brings the "Dev team" seeded by migration V4 up to the bundled template at startup, so a first start does not offer
 * a pointless replace. A team the user edited, or a missing one, is left alone.
 */
@Component
public class SeededTeamUpgrade {

    static final String ITEM_ID = "team.dev-team";
    /** Exactly the roles V4 inserted; a test compares them with the real migration. */
    static final List<RoleDraft> LEGACY_ROLES = List.of(
            new RoleDraft("architect", "Leads the task: splits it into steps, decides the design, reviews the result. Not a subagent, this is the main session.",
                    "You lead the team. Break the task into small steps, delegate implementation to the developer and verification to the tester, then review what they report and decide whether the task is done.",
                    "opus", "high", null, true),
            new RoleDraft("developer", "Use to write or change code: implement a feature, fix a bug, refactor. Give it one concrete, self-contained step.",
                    "You are a careful developer. Implement exactly the step you are given, keep changes small and consistent with the surrounding code, and report which files you changed.",
                    "sonnet", "medium", null, false),
            new RoleDraft("tester", "Use to check work: run the build and tests, try the change, and report failures. Use after the developer finished a step.",
                    "You are a tester. Run the relevant build and tests, verify the change behaves as asked, and report precisely what passed and what failed. Do not change production code.",
                    "haiku", "low", "Read, Grep, Glob, Bash", false));
    private static final Logger log = LoggerFactory.getLogger(SeededTeamUpgrade.class);

    private final TeamService teams;
    private final TeamsInstaller installer;
    private final AuthorProfileLoader loader;
    private final AuthorDecisionService decisions;

    public SeededTeamUpgrade(TeamService teams, TeamsInstaller installer, AuthorProfileLoader loader, AuthorDecisionService decisions) {
        this.teams = teams;
        this.installer = installer;
        this.loader = loader;
        this.decisions = decisions;
    }

    @PostConstruct
    public void upgrade() {
        try {
            var item = loader.catalog().byId(ITEM_ID).orElseThrow();
            var template = loader.teamTemplate(ITEM_ID);
            var current = teams.list().stream().filter(team -> team.name().equals(template.name())).findFirst();
            if (current.isEmpty()) {
                return;
            }
            var roles = current.get().roles().stream().map(role -> role.draft()).toList();
            if (roles.equals(LEGACY_ROLES)) {
                if (installer.install(List.of(item)).hasFailures()) {
                    log.warn("Seeded team upgrade failed");
                    return;
                }
            } else if (!roles.equals(template.roles())) {
                return;
            }
            var decision = decisions.findAll().get(ITEM_ID);
            if (decision == null || decision.choice() != AuthorChoice.INSTALLED || decision.version() < item.version()) {
                decisions.record(ITEM_ID, AuthorChoice.INSTALLED, item.version());
            }
        } catch (RuntimeException e) {
            log.warn("Seeded team upgrade skipped: {}", e.getClass().getSimpleName());
        }
    }
}
