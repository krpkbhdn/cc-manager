package com.github.krpkbhdn.ccmanager.claude.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TranscriptServiceTest {

    @TempDir
    Path dir;

    private TranscriptService service(Path root) {
        return new TranscriptService(JsonMapper.builder().build(), root);
    }

    private static String user(String text) {
        return "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"" + text + "\"}}";
    }

    @Test
    void rendersPromptAssistantToolCallAndResultAndSkipsNoise() throws Exception {
        var file = dir.resolve("agent.jsonl");
        Files.writeString(file, String.join("\n",
                user("Fix it"),
                "{\"type\":\"attachment\",\"attachment\":{}}",
                "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"thinking\",\"thinking\":\"hm\"},"
                        + "{\"type\":\"text\",\"text\":\"On it\"},{\"type\":\"tool_use\",\"name\":\"Bash\",\"input\":{\"command\":\"ls\"}}]}}",
                "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"content\":\"a.txt\"}]}}",
                "not json", ""));

        var entries = service(dir).read(file.toString());

        assertThat(entries).extracting(TranscriptEntry::kind)
                .containsExactly(Kind.PROMPT, Kind.ASSISTANT, Kind.TOOL_CALL, Kind.TOOL_RESULT);
        assertThat(entries.get(2).title()).isEqualTo("Bash");
        assertThat(entries.get(2).text()).contains("ls");
        assertThat(entries.get(3).text()).isEqualTo("a.txt");
    }

    @Test
    void keepsTheLastEntriesAndSkipsAnUnfinishedLastLine() throws Exception {
        var file = dir.resolve("long.jsonl");
        var lines = String.join("\n", IntStream.range(0, 500).mapToObj(i -> user("m" + i)).toList());
        Files.writeString(file, lines + "\n" + user("half").substring(0, 20));

        var entries = service(dir).read(file.toString());

        assertThat(entries).hasSize(300);
        assertThat(entries.getLast().text()).isEqualTo("m499");
        assertThat(entries.getFirst().text()).isEqualTo("m200");
    }

    @Test
    void filesOutsideTheProjectsDirectoryAreNotRead() throws Exception {
        var projects = Files.createDirectory(dir.resolve("projects"));
        var outside = dir.resolve("secret.jsonl");
        Files.writeString(outside, user("secret") + "\n");

        assertThat(service(projects).read(outside.toString())).isEmpty();
        assertThat(service(projects).read(projects.resolve("../secret.jsonl").toString())).isEmpty();
    }

    @Test
    void missingFileGivesNoEntries() {
        assertThat(service(dir).read(dir.resolve("none.jsonl").toString())).isEmpty();
        assertThat(service(dir).read(null)).isEmpty();
    }

    private String titleOf(String... lines) throws Exception {
        var file = dir.resolve("t.jsonl");
        Files.writeString(file, String.join("\n", lines) + "\n");
        return service(dir).title(file.toString()).orElse(null);
    }

    @Test
    void latestGeneratedTitleWins() throws Exception {
        assertThat(titleOf(user("a"), "{\"type\":\"ai-title\",\"aiTitle\":\"First\"}", user("b"),
                "{\"type\":\"ai-title\",\"aiTitle\":\"Second\"}")).isEqualTo("Second");
    }

    @Test
    void customTitleBeatsGeneratedOne() throws Exception {
        assertThat(titleOf("{\"type\":\"custom-title\",\"customTitle\":\"Mine\"}", "{\"type\":\"ai-title\",\"aiTitle\":\"Generated\"}"))
                .isEqualTo("Mine");
    }

    @Test
    void noTitleLineGivesEmpty() throws Exception {
        assertThat(titleOf(user("a"))).isNull();
        assertThat(service(dir).title(null)).isEmpty();
    }

    @Test
    void malformedAndBlankTitleLinesAreSkipped() throws Exception {
        assertThat(titleOf("{\"type\":\"ai-title\",\"aiTitle\":\"Good\"}", "{not json", "{\"type\":\"ai-title\",\"aiTitle\":\"  \"}"))
                .isEqualTo("Good");
    }
}
