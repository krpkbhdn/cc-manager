package com.github.krpkbhdn.ccmanager.claude.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HookOutputTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void allowWithoutInputOrPermissionsIsAPlainAllowDecision() {
        assertThat(HookOutput.allow(null, List.of()))
                .isEqualTo("{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\",\"decision\":{\"behavior\":\"allow\"}}}");
    }

    @Test
    void allowCarriesTheUpdatedInputVerbatim() {
        var input = MAPPER.readTree("{\"questions\":[{\"question\":\"q\"}],\"answers\":{\"q\":\"a\"}}");

        assertThat(HookOutput.allow(input, List.of())).isEqualTo("{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\","
                + "\"decision\":{\"behavior\":\"allow\",\"updatedInput\":{\"questions\":[{\"question\":\"q\"}],\"answers\":{\"q\":\"a\"}}}}}");
    }

    @Test
    void emptyUpdatedInputObjectIsKeptNotDropped() {
        assertThat(HookOutput.allow(MAPPER.createObjectNode(), List.of())).contains("\"updatedInput\":{}");
    }

    @Test
    void allowListsUpdatedPermissionsInOrder() {
        var rule = MAPPER.readTree("{\"type\":\"addRules\"}");

        assertThat(HookOutput.allow(null, List.of(rule, HookOutput.acceptEditsForSession()))).isEqualTo(
                "{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\",\"decision\":{\"behavior\":\"allow\","
                        + "\"updatedPermissions\":[{\"type\":\"addRules\"},{\"type\":\"setMode\",\"mode\":\"acceptEdits\",\"destination\":\"session\"}]}}}");
    }

    @Test
    void acceptEditsForSessionIsTheSetModeUpdate() {
        assertThat(HookOutput.acceptEditsForSession().toString())
                .isEqualTo("{\"type\":\"setMode\",\"mode\":\"acceptEdits\",\"destination\":\"session\"}");
    }

    @Test
    void denyCarriesTheFeedbackMessage() {
        assertThat(HookOutput.deny("no \"way\"")).isEqualTo("{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\","
                + "\"decision\":{\"behavior\":\"deny\",\"message\":\"no \\\"way\\\"\"}}}");
    }

    @Test
    void elicitationAcceptCarriesTheContent() {
        assertThat(HookOutput.elicitation("accept", MAPPER.readTree("{\"name\":\"x\"}")))
                .isEqualTo("{\"hookSpecificOutput\":{\"hookEventName\":\"Elicitation\",\"action\":\"accept\",\"content\":{\"name\":\"x\"}}}");
    }

    @Test
    void elicitationDeclineAndCancelOmitContentWhenNull() {
        assertThat(HookOutput.elicitation("decline", null))
                .isEqualTo("{\"hookSpecificOutput\":{\"hookEventName\":\"Elicitation\",\"action\":\"decline\"}}");
        assertThat(HookOutput.elicitation("cancel", null)).doesNotContain("content");
    }
}
