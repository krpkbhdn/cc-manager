package com.github.krpkbhdn.ccmanager.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TeamAgentsJsonTest {

    private static final RoleDraft LEAD = new RoleDraft("architect", "Leads.", "Plan the work.", "opus", "high", null, true);

    private static Team team(RoleDraft... drafts) {
        var roles = new ArrayList<TeamRole>();
        for (int i = 0; i < drafts.length; i++) {
            roles.add(new TeamRole(i + 1, drafts[i], i));
        }
        return new Team(1, "Dev team", roles);
    }

    @Test
    void agentsJsonIsGoldenAndSkipsTheLead() {
        var json = TeamAgentsJson.agents(team(LEAD,
                new RoleDraft("tester", "Checks work.", "You test.", "haiku", "low", "Read, Bash", false),
                new RoleDraft("developer", "Writes code.", "You code.", null, null, null, false)));

        assertThat(json).isEqualTo("{\"tester\":{\"description\":\"Checks work.\",\"prompt\":\"You test.\",\"model\":\"haiku\","
                + "\"effort\":\"low\",\"tools\":[\"Read\",\"Bash\"]},\"developer\":{\"description\":\"Writes code.\",\"prompt\":\"You code.\"}}");
    }

    @Test
    void quotesNewlinesBackslashesAndUnicodeSurviveARoundTrip() {
        var prompt = "Say \"hi\"\nthen C:\\temp \u2014 \u043f\u0440\u0438\u0432\u0456\u0442 \uD83D\uDE00";

        var json = TeamAgentsJson.agents(team(LEAD, new RoleDraft("dev", "d \"x\"", prompt, null, null, null, false)));

        assertThat(json).doesNotContain("\n");
        var parsed = JsonMapper.builder().build().readTree(json);
        assertThat(parsed.get("dev").get("prompt").asString()).isEqualTo(prompt);
        assertThat(parsed.get("dev").get("description").asString()).isEqualTo("d \"x\"");
    }

    @Test
    void leadAddendumListsRolesWithDescriptionsAfterTheLeadPrompt() {
        var text = TeamAgentsJson.leadAddendum(team(LEAD, new RoleDraft("tester", "Checks work.", "You test.", null, null, null, false)));

        assertThat(text).startsWith("Plan the work.")
                .contains("lead of the team \"Dev team\"")
                .contains("- tester: Checks work.")
                .doesNotContain("- architect");
    }

    @Test
    void toolsListIgnoresBlankEntries() {
        var json = TeamAgentsJson.agents(team(LEAD, new RoleDraft("dev", "d", "p", null, null, "Read,, Grep ", false)));

        assertThat(JsonMapper.builder().build().readTree(json).get("dev").get("tools").toString()).isEqualTo("[\"Read\",\"Grep\"]");
    }
}
