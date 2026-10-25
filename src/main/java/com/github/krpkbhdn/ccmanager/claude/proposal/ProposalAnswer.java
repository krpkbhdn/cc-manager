package com.github.krpkbhdn.ccmanager.claude.proposal;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** The user's reply to a proposal; each variant knows which proposals it fits and renders the exact hook output Claude Code expects. */
public sealed interface ProposalAnswer {

    String DEFAULT_DENY_MESSAGE = "Rejected by the user in cc-manager";

    boolean fits(Proposal proposal);

    /** The body of the held hook's 200 response. Only called for a proposal this answer fits. */
    String hookOutput(Proposal proposal);

    /** Answers by question text: the chosen option labels or free text; a single-choice question uses the first entry. */
    record QuestionsAnswer(Map<String, List<String>> answers) implements ProposalAnswer {

        public QuestionsAnswer {
            answers = Map.copyOf(answers);
        }

        @Override
        public boolean fits(Proposal proposal) {
            return proposal instanceof QuestionsProposal;
        }

        @Override
        public String hookOutput(Proposal proposal) {
            var asked = (QuestionsProposal) proposal;
            var updatedInput = asked.input().deepCopy();
            var chosen = updatedInput.putObject("answers");
            for (var question : asked.questions()) {
                var values = answers.get(question.text());
                if (values == null) {
                    continue;
                }
                if (question.multiSelect()) {
                    values.forEach(chosen.putArray(question.text())::add);
                } else {
                    chosen.put(question.text(), values.isEmpty() ? "" : values.getFirst());
                }
            }
            return HookOutput.allow(updatedInput, List.of());
        }
    }

    /** Approves the plan; acceptEdits also switches the session to auto-accepted edits, otherwise it returns to manual approval. */
    record PlanApproval(boolean acceptEdits) implements ProposalAnswer {

        @Override
        public boolean fits(Proposal proposal) {
            return proposal instanceof PlanProposal;
        }

        @Override
        public String hookOutput(Proposal proposal) {
            var modeChange = acceptEdits ? List.of(HookOutput.acceptEditsForSession()) : List.<JsonNode>of();
            return HookOutput.allow(((PlanProposal) proposal).input(), modeChange);
        }
    }

    record PlanRejection(String feedback) implements ProposalAnswer {

        @Override
        public boolean fits(Proposal proposal) {
            return proposal instanceof PlanProposal;
        }

        @Override
        public String hookOutput(Proposal proposal) {
            return HookOutput.deny(feedback.isBlank() ? DEFAULT_DENY_MESSAGE : feedback);
        }
    }

    /** Allows the tool call; the remembered suggestions (copied from the proposal's own) are applied, none means allow once. */
    record PermissionAllow(List<JsonNode> remembered) implements ProposalAnswer {

        public PermissionAllow {
            remembered = List.copyOf(remembered);
        }

        public static PermissionAllow once() {
            return new PermissionAllow(List.of());
        }

        @Override
        public boolean fits(Proposal proposal) {
            return proposal instanceof PermissionProposal;
        }

        @Override
        public String hookOutput(Proposal proposal) {
            return HookOutput.allow(null, remembered);
        }
    }

    /** Refuses a question set, plan or tool call; the reason goes back to Claude. */
    record Denial(String reason) implements ProposalAnswer {

        @Override
        public boolean fits(Proposal proposal) {
            return !(proposal instanceof FormProposal);
        }

        @Override
        public String hookOutput(Proposal proposal) {
            return HookOutput.deny(reason.isBlank() ? DEFAULT_DENY_MESSAGE : reason);
        }
    }

    /** Reply to an MCP form; content (property to value) is sent only when accepted. */
    record FormAnswer(Action action, ObjectNode content) implements ProposalAnswer {

        public enum Action { ACCEPT, DECLINE, CANCEL }

        public static FormAnswer accept(ObjectNode content) {
            return new FormAnswer(Action.ACCEPT, content);
        }

        public static FormAnswer decline() {
            return new FormAnswer(Action.DECLINE, null);
        }

        public static FormAnswer cancel() {
            return new FormAnswer(Action.CANCEL, null);
        }

        @Override
        public boolean fits(Proposal proposal) {
            return proposal instanceof FormProposal;
        }

        @Override
        public String hookOutput(Proposal proposal) {
            return HookOutput.elicitation(action.name().toLowerCase(Locale.ROOT), action == Action.ACCEPT ? content : null);
        }
    }
}
