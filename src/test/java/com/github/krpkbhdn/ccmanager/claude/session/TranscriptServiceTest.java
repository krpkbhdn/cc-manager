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
        return new TranscriptService(JsonMapper.builder().build(), root, new TranscriptProperties(300, 1 << 20, 64 * 1024, 512 * 1024));
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
    void titleIsCachedUntilTheFileChanges() throws Exception {
        var file = dir.resolve("t.jsonl");
        Files.writeString(file, "{\"type\":\"ai-title\",\"aiTitle\":\"One\"}\n" + user("x") + "\n");
        var service = service(dir);
        assertThat(service.title(file.toString())).contains("One");

        var modified = Files.getLastModifiedTime(file);
        Files.writeString(file, "{\"type\":\"ai-title\",\"aiTitle\":\"Two\"}\n" + user("x") + "\n");
        Files.setLastModifiedTime(file, modified);
        assertThat(service.title(file.toString())).contains("One");

        Files.writeString(file, "{\"type\":\"ai-title\",\"aiTitle\":\"Three!\"}\n" + user("x") + "\n");
        assertThat(service.title(file.toString())).contains("Three!");
    }

    @Test
    void cachedCustomTitleSurvivesAGeneratedOneInTheShortRefreshTail() throws Exception {
        var file = dir.resolve("renamed.jsonl");
        Files.writeString(file, "{\"type\":\"custom-title\",\"customTitle\":\"Mine\"}\n" + user("x") + "\n");
        var service = service(dir);
        assertThat(service.title(file.toString())).contains("Mine");

        Files.writeString(file, user("y".repeat(1 << 19)) + "\n{\"type\":\"ai-title\",\"aiTitle\":\"Generated\"}\n" + user("z") + "\n");

        assertThat(service.title(file.toString())).contains("Mine");
    }

    @Test
    void titleStaysWhenALaterGrowthOfTheFilePushesItOutOfTheTail() throws Exception {
        var file = dir.resolve("big.jsonl");
        Files.writeString(file, "{\"type\":\"ai-title\",\"aiTitle\":\"Kept\"}\n" + user("x") + "\n");
        var service = service(dir);
        assertThat(service.title(file.toString())).contains("Kept");

        Files.writeString(file, user("y".repeat(1 << 21)) + "\n" + user("z") + "\n");

        assertThat(service.title(file.toString())).contains("Kept");
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
    void windowAroundALineOffsetReturnsTheEntryIndexAndStaysInsideProjects() throws Exception {
        var projects = Files.createDirectory(dir.resolve("projects"));
        var file = projects.resolve("w.jsonl");
        var first = user("a") + "\n";
        Files.writeString(file, first + user("b") + "\n" + user("c") + "\n");

        var window = service(projects).readWindow(file.toString(), first.length());

        assertThat(window.entries()).extracting(TranscriptEntry::text).containsExactly("a", "b", "c");
        assertThat(window.index()).isEqualTo(1);
        var outside = dir.resolve("o.jsonl");
        Files.writeString(outside, first);
        assertThat(service(projects).readWindow(outside.toString(), 0).entries()).isEmpty();
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
