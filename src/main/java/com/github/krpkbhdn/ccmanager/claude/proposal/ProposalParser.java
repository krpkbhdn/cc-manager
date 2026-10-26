package com.github.krpkbhdn.ccmanager.claude.proposal;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal.Suggestion;
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Option;
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Question;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Turns a PermissionRequest or Elicitation hook call into a proposal. A payload that does not make sense yields none, never an exception. */
final class ProposalParser {

    private static final String DEFAULT_FORM_MODE = "form";
    private static final Set<String> FORM_TYPES = Set.of("string", "number", "integer", "boolean");

    private ProposalParser() {}

    static Optional<Proposal> parse(HookEvent event) {
        try {
            return switch (event.type()) {
                case PERMISSION_REQUEST -> parsePermissionRequest(event);
                case ELICITATION -> parseForm(event);
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Proposal> parsePermissionRequest(HookEvent event) {
        var tool = event.text("tool_name");
        var input = event.payload().path("tool_input");
        var inputObject = input.isObject() ? (ObjectNode) input : HookOutput.MAPPER.createObjectNode();
        return switch (tool) {
            case "" -> Optional.empty();
            case QuestionsProposal.TOOL_NAME -> parseQuestions(event, inputObject);
            case PlanProposal.TOOL_NAME -> Optional.of(new PlanProposal(newId(), event.ccmSessionId(), event.receivedAt(),
                    event.agentId(), inputObject, inputObject.path("plan").asString(""), inputObject.path("planFilePath").asString("")));
            default -> Optional.of(new PermissionProposal(newId(), event.ccmSessionId(), event.receivedAt(), event.agentId(), tool,
                    inputObject, suggestionsOf(event.payload().path("permission_suggestions"))));
        };
    }

    private static Optional<Proposal> parseQuestions(HookEvent event, ObjectNode input) {
        var questions = new ArrayList<Question>();
        for (var question : input.path("questions")) {
            var text = question.path("question").asString("");
            var options = optionsOf(question.path("options"));
            if (text.isBlank() || options.isEmpty()) {
                return Optional.empty();
            }
            questions.add(new Question(text, question.path("header").asString(""), options.get(),
                    question.path("multiSelect").asBoolean(false)));
        }
        if (questions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QuestionsProposal(newId(), event.ccmSessionId(), event.receivedAt(), event.agentId(), input, questions));
    }

    /** Empty when there are no options or one has no label: the dialog could not show it, so the terminal keeps it. */
    private static Optional<List<Option>> optionsOf(JsonNode options) {
        var result = new ArrayList<Option>();
        for (var option : options) {
            var label = option.path("label").asString("");
            if (label.isBlank()) {
                return Optional.empty();
            }
            result.add(new Option(label, option.path("description").asString(""), option.path("preview").asString("")));
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private static List<Suggestion> suggestionsOf(JsonNode suggestions) {
        var result = new ArrayList<Suggestion>();
        suggestions.forEach(suggestion -> result.add(new Suggestion(suggestion, SuggestionDescriber.describe(suggestion))));
        return result;
    }

    private static Optional<Proposal> parseForm(HookEvent event) {
        var server = event.text("mcp_server_name");
        if (server.isEmpty()) {
            return Optional.empty();
        }
        var mode = event.text("mode");
        var schema = event.payload().path("requested_schema");
        if (!"url".equals(mode) && hasUnshowableProperty(schema.path("properties"))) {
            return Optional.empty();
        }
        return Optional.of(new FormProposal(newId(), event.ccmSessionId(), event.receivedAt(), event.agentId(), server,
                event.text("message"), mode.isEmpty() ? DEFAULT_FORM_MODE : mode, event.text("url"), event.text("elicitation_id"),
                schema.isObject() ? schema : HookOutput.MAPPER.createObjectNode()));
    }

    /** A property the form cannot render (array, object, unknown type, no choices): the terminal keeps the request. */
    private static boolean hasUnshowableProperty(JsonNode properties) {
        for (var property : properties.values()) {
            var showableType = FORM_TYPES.contains(property.path("type").asString(""));
            if (!showableType && !property.has("enum") && !property.has("oneOf")) {
                return true;
            }
        }
        return false;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
