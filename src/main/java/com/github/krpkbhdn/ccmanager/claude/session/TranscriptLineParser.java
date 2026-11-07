package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry.Kind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns one Claude transcript JSONL line into readable entries; unknown or broken lines yield nothing instead of failing. */
public class TranscriptLineParser {

    private static final int TEXT_LIMIT = 6000;
    private static final int INPUT_LIMIT = 400;

    private final JsonMapper mapper;

    public TranscriptLineParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<TranscriptLine> parse(String line) {
        try {
            var node = mapper.readTree(line);
            var uuid = node.path("uuid").asString("");
            var content = node.path("message").path("content");
            var entries = switch (node.path("type").asString("")) {
                case "user" -> blocks(content, Kind.PROMPT, "You", uuid);
                case "assistant" -> blocks(content, Kind.ASSISTANT, "Agent", uuid);
                default -> List.<TranscriptEntry>of();
            };
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new TranscriptLine(uuid, node.path("sessionId").asString(""), node.path("cwd").asString(""),
                    instantOf(node.path("timestamp").asString("")), entries));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Instant instantOf(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (RuntimeException e) {
            return Instant.EPOCH;
        }
    }

    private static List<TranscriptEntry> blocks(JsonNode content, Kind textKind, String speaker, String uuid) {
        if (content.isString()) {
            return List.of(new TranscriptEntry(textKind, speaker, cut(content.asString(), TEXT_LIMIT), "", uuid));
        }
        var entries = new ArrayList<TranscriptEntry>();
        for (var block : content) {
            switch (block.path("type").asString("")) {
                case "text" -> entries.add(new TranscriptEntry(textKind, speaker, cut(block.path("text").asString(""), TEXT_LIMIT), "", uuid));
                case "tool_use" -> {
                    var input = block.path("input").toString();
                    entries.add(new TranscriptEntry(Kind.TOOL_CALL, block.path("name").asString("tool"), cut(input, INPUT_LIMIT),
                            block.path("id").asString(""), uuid, input));
                }
                case "tool_result" -> entries.add(new TranscriptEntry(Kind.TOOL_RESULT, "Result",
                        cut(resultText(block.path("content")), TEXT_LIMIT), block.path("tool_use_id").asString(""), uuid));
                default -> { }
            }
        }
        return entries;
    }

    private static String resultText(JsonNode content) {
        if (content.isString()) {
            return content.asString();
        }
        var text = new StringBuilder();
        content.forEach(part -> text.append(part.path("text").asString("")));
        return text.toString();
    }

    private static String cut(String value, int limit) {
        return ActivityFormatter.cut(value.strip(), limit);
    }
}
