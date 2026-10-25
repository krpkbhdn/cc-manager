package com.github.krpkbhdn.ccmanager.claude.proposal;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/** AskUserQuestion. The input is kept verbatim because the answer must echo it. */
public record QuestionsProposal(String id, String ccmSessionId, Instant createdAt, String agentId, ObjectNode input,
        List<Question> questions) implements Proposal {

    public static final String TOOL_NAME = "AskUserQuestion";

    public QuestionsProposal {
        questions = List.copyOf(questions);
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    public record Question(String text, String header, List<Option> options, boolean multiSelect) {

        public Question {
            options = List.copyOf(options);
        }
    }

    /** preview is empty when the option has none. */
    public record Option(String label, String description, String preview) {}
}
