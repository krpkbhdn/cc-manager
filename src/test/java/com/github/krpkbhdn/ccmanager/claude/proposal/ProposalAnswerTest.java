package com.github.krpkbhdn.ccmanager.claude.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.Denial;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.FormAnswer;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanApproval;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanRejection;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.QuestionsAnswer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ProposalAnswerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant AT = Instant.parse("2026-10-25T10:00:00Z");
    private static final String QUESTIONS = """
            {"questions":[{"question":"Which db?","header":"DB","multiSelect":false,"options":[{"label":"SQLite"},{"label":"Postgres"}]},
            {"question":"Extras?","header":"E","multiSelect":true,"options":[{"label":"A"},{"label":"B"}]}]}""";

    private final QuestionsProposal questions = (QuestionsProposal) ProposalParser.parse(new HookEvent("s", HookEventType.PERMISSION_REQUEST,
            "PermissionRequest", json("{\"tool_name\":\"AskUserQuestion\",\"tool_input\":" + QUESTIONS + "}"), AT)).orElseThrow();
    private final PlanProposal plan = new PlanProposal("p", "s", AT, "", MAPPER.createObjectNode(), "", "");
    private final PermissionProposal permission = new PermissionProposal("t", "s", AT, "", "Bash", MAPPER.createObjectNode(), List.of());
    private final FormProposal form = new FormProposal("f", "s", AT, "", "jira", "m", "form", "", "", MAPPER.createObjectNode());

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    @Test
    void questionsAnswerEchoesQuestionsAndRendersSingleAsStringAndMultiAsArray() {
        var output = new QuestionsAnswer(Map.of("Which db?", List.of("SQLite"), "Extras?", List.of("A", "B"))).hookOutput(questions);

        assertThat(json(output)).isEqualTo(json("""
                {"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow","updatedInput":{
                "questions":%s,"answers":{"Which db?":"SQLite","Extras?":["A","B"]}}}}}""".formatted(json(QUESTIONS).path("questions"))));
    }

    @Test
    void questionsAnswerAcceptsFreeText() {
        var output = new QuestionsAnswer(Map.of("Which db?", List.of("something else"))).hookOutput(questions);

        assertThat(json(output).at("/hookSpecificOutput/decision/updatedInput/answers/Which db?").asString()).isEqualTo("something else");
    }

    @Test
    void planApprovalEchoesEmptyInputAndOptionallySwitchesToAcceptEdits() {
        assertThat(json(new PlanApproval(false).hookOutput(plan))).isEqualTo(json(
                "{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\",\"decision\":{\"behavior\":\"allow\",\"updatedInput\":{}}}}"));
        assertThat(json(new PlanApproval(true).hookOutput(plan))).isEqualTo(json("""
                {"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow","updatedInput":{},
                "updatedPermissions":[{"type":"setMode","mode":"acceptEdits","destination":"session"}]}}}"""));
    }

    @Test
    void planRejectionAndDenialCarryTheFeedback() {
        var expected = json("{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\",\"decision\":{\"behavior\":\"deny\",\"message\":\"too big\"}}}");

        assertThat(json(new PlanRejection("too big").hookOutput(plan))).isEqualTo(expected);
        assertThat(json(new Denial("too big").hookOutput(permission))).isEqualTo(expected);
        assertThat(json(new Denial(" ").hookOutput(permission)).at("/hookSpecificOutput/decision/message").asString()).isNotBlank();
    }

    @Test
    void permissionAllowOnceHasNoUpdatedPermissionsAndRememberedOnesAreCopiedVerbatim() {
        var rule = json("{\"type\":\"addRules\",\"rules\":[{\"toolName\":\"Bash\",\"ruleContent\":\"npm test:*\"}],\"behavior\":\"allow\",\"destination\":\"localSettings\"}");

        assertThat(json(PermissionAllow.once().hookOutput(permission))).isEqualTo(json(
                "{\"hookSpecificOutput\":{\"hookEventName\":\"PermissionRequest\",\"decision\":{\"behavior\":\"allow\"}}}"));
        assertThat(json(new PermissionAllow(List.of(rule)).hookOutput(permission)).at("/hookSpecificOutput/decision/updatedPermissions/0"))
                .isEqualTo(rule);
    }

    @Test
    void formAnswerRendersActionAndContentOnlyWhenAccepted() {
        var content = (ObjectNode) json("{\"key\":\"ABC\"}");

        assertThat(json(FormAnswer.accept(content).hookOutput(form))).isEqualTo(json(
                "{\"hookSpecificOutput\":{\"hookEventName\":\"Elicitation\",\"action\":\"accept\",\"content\":{\"key\":\"ABC\"}}}"));
        assertThat(json(FormAnswer.decline().hookOutput(form))).isEqualTo(json(
                "{\"hookSpecificOutput\":{\"hookEventName\":\"Elicitation\",\"action\":\"decline\"}}"));
        assertThat(json(FormAnswer.cancel().hookOutput(form)).at("/hookSpecificOutput/action").asString()).isEqualTo("cancel");
    }

    @Test
    void answersFitOnlyTheirOwnKind() {
        assertThat(new QuestionsAnswer(Map.of()).fits(questions)).isTrue();
        assertThat(new QuestionsAnswer(Map.of()).fits(plan)).isFalse();
        assertThat(new PlanApproval(false).fits(plan)).isTrue();
        assertThat(new PlanApproval(false).fits(permission)).isFalse();
        assertThat(PermissionAllow.once().fits(permission)).isTrue();
        assertThat(PermissionAllow.once().fits(form)).isFalse();
        assertThat(new Denial("x").fits(questions)).isTrue();
        assertThat(new Denial("x").fits(form)).isFalse();
        assertThat(FormAnswer.cancel().fits(form)).isTrue();
        assertThat(FormAnswer.cancel().fits(plan)).isFalse();
    }
}
