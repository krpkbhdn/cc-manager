package com.github.krpkbhdn.ccmanager.claude.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProposalParserTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant AT = Instant.parse("2026-10-25T10:00:00Z");

    private static HookEvent event(HookEventType type, String json) {
        return new HookEvent("ccm-1", type, type.wireName(), MAPPER.readTree(json), AT);
    }

    private static HookEvent permission(String json) {
        return event(HookEventType.PERMISSION_REQUEST, json);
    }

    @Test
    void parsesQuestionsWithOptionsAndMultiSelect() {
        var proposal = ProposalParser.parse(permission("""
                {"tool_name":"AskUserQuestion","agent_id":"sub-1","tool_input":{"questions":[
                  {"question":"Which db?","header":"DB","multiSelect":false,"options":[
                    {"label":"SQLite","description":"embedded","preview":"<p>x</p>"},{"label":"Postgres"}]},
                  {"question":"Which extras?","header":"Extras","multiSelect":true,"options":[{"label":"A"},{"label":"B"}]}]}}
                """));

        var questions = (QuestionsProposal) proposal.orElseThrow();
        assertThat(questions.ccmSessionId()).isEqualTo("ccm-1");
        assertThat(questions.createdAt()).isEqualTo(AT);
        assertThat(questions.agentId()).isEqualTo("sub-1");
        assertThat(questions.questions()).extracting(QuestionsProposal.Question::text).containsExactly("Which db?", "Which extras?");
        assertThat(questions.questions().get(0).options()).containsExactly(
                new QuestionsProposal.Option("SQLite", "embedded", "<p>x</p>"), new QuestionsProposal.Option("Postgres", "", ""));
        assertThat(questions.questions().get(1).multiSelect()).isTrue();
        assertThat(questions.id()).isNotBlank();
    }

    @Test
    void parsesPlanAndAcceptsEmptyInput() {
        var withPlan = ProposalParser.parse(permission("{\"tool_name\":\"ExitPlanMode\",\"tool_input\":{\"plan\":\"# Do it\",\"planFilePath\":\"p.md\"}}"));
        var empty = ProposalParser.parse(permission("{\"tool_name\":\"ExitPlanMode\",\"tool_input\":{}}"));

        assertThat(((PlanProposal) withPlan.orElseThrow()).plan()).isEqualTo("# Do it");
        assertThat(((PlanProposal) withPlan.orElseThrow()).planFilePath()).isEqualTo("p.md");
        assertThat(((PlanProposal) empty.orElseThrow()).plan()).isEmpty();
    }

    @Test
    void parsesToolPermissionWithDescribedSuggestionsKeptVerbatim() {
        var proposal = ProposalParser.parse(permission("""
                {"tool_name":"Bash","tool_input":{"command":"npm test"},"permission_suggestions":[
                  {"type":"setMode","mode":"acceptEdits","destination":"session"},
                  {"type":"addRules","rules":[{"toolName":"Bash","ruleContent":"npm test:*"}],"behavior":"allow","destination":"localSettings"},
                  {"type":"addDirectories","directories":["C:\\\\work","D:\\\\data"],"destination":"session"}]}
                """));

        var tool = (PermissionProposal) proposal.orElseThrow();
        assertThat(tool.toolName()).isEqualTo("Bash");
        assertThat(tool.toolInput().path("command").asString()).isEqualTo("npm test");
        assertThat(tool.suggestions()).extracting(PermissionProposal.Suggestion::description).containsExactly(
                "Allow edits for this session", "Always allow `Bash(npm test:*)`", "Allow access to C:\\work, D:\\data");
        assertThat(tool.suggestions().get(0).raw().path("mode").asString()).isEqualTo("acceptEdits");
    }

    @Test
    void parsesFormWithDefaults() {
        var proposal = ProposalParser.parse(event(HookEventType.ELICITATION, """
                {"mcp_server_name":"jira","message":"Which project?","requested_schema":{"properties":{"key":{"type":"string"}},"required":["key"]}}
                """));

        var form = (FormProposal) proposal.orElseThrow();
        assertThat(form.serverName()).isEqualTo("jira");
        assertThat(form.message()).isEqualTo("Which project?");
        assertThat(form.mode()).isEqualTo("form");
        assertThat(form.url()).isEmpty();
        assertThat(form.requestedSchema().path("required").get(0).asString()).isEqualTo("key");
    }

    @Test
    void malformedOrForeignInputYieldsNoProposal() {
        assertThat(ProposalParser.parse(permission("{}"))).isEmpty();
        assertThat(ProposalParser.parse(permission("{\"tool_name\":\"AskUserQuestion\",\"tool_input\":{}}"))).isEmpty();
        assertThat(ProposalParser.parse(permission("{\"tool_name\":\"AskUserQuestion\",\"tool_input\":{\"questions\":[{\"question\":\"\"}]}}"))).isEmpty();
        assertThat(ProposalParser.parse(permission(
                "{\"tool_name\":\"AskUserQuestion\",\"tool_input\":{\"questions\":[{\"question\":\"Q\",\"options\":[{\"description\":\"d\"}]}]}}"))).isEmpty();
        assertThat(ProposalParser.parse(event(HookEventType.ELICITATION, "{\"message\":\"no server\"}"))).isEmpty();
        assertThat(ProposalParser.parse(event(HookEventType.STOP, "{\"tool_name\":\"Bash\"}"))).isEmpty();
    }

    @Test
    void toolPermissionWithoutInputStillParses() {
        var proposal = ProposalParser.parse(permission("{\"tool_name\":\"Write\",\"tool_input\":\"oops\"}"));

        assertThat(((PermissionProposal) proposal.orElseThrow()).toolInput().isObject()).isTrue();
    }
}
