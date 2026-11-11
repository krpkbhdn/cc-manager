package com.github.krpkbhdn.ccmanager.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SettingsFragmentMergerTest {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    Path directory;

    static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    @Test
    void addsMissingValuesAndMergesObjectsRecursively() {
        var result = SettingsFragmentMerger.merge(json("{\"a\":{\"x\":1},\"keep\":true}"), json("{\"a\":{\"y\":2},\"b\":\"new\"}"));

        assertThat(result.merged()).isEqualTo(json("{\"a\":{\"x\":1,\"y\":2},\"keep\":true,\"b\":\"new\"}"));
        assertThat(result.conflicts()).isEmpty();
        assertThat(result.changed()).isTrue();
    }

    @Test
    void mergeFileKeepsASymlinkAndWritesThroughIt() throws Exception {
        Files.writeString(directory.resolve("real.json"), "{}");
        try {
            Files.createSymbolicLink(directory.resolve("settings.json"), Path.of("real.json"));
        } catch (java.io.IOException | UnsupportedOperationException e) {
            org.assertj.core.api.Assumptions.assumeThat(false).as("symlinks cannot be created here").isTrue();
        }

        SettingsFragmentMerger.mergeFile(directory.resolve("settings.json"), json("{\"a\":1}"));

        assertThat(Files.isSymbolicLink(directory.resolve("settings.json"))).isTrue();
        assertThat(json(Files.readString(directory.resolve("real.json")))).isEqualTo(json("{\"a\":1}"));
    }

    @Test
    void arraysTakeTheUnionKeepingOrderWithoutDuplicates() {
        var result = SettingsFragmentMerger.merge(json("{\"l\":[\"b\",\"a\"]}"), json("{\"l\":[\"a\",\"c\"]}"));

        assertThat(result.merged()).isEqualTo(json("{\"l\":[\"b\",\"a\",\"c\"]}"));
    }

    @Test
    void differingScalarIsAConflictAndStaysUntouched() {
        var result = SettingsFragmentMerger.merge(json("{\"attribution\":{\"commit\":\"Co-Authored-By\"}}"),
                json("{\"attribution\":{\"commit\":\"\",\"pr\":\"\"}}"));

        assertThat(result.conflicts()).containsExactly("attribution.commit");
        assertThat(result.merged()).isEqualTo(json("{\"attribution\":{\"commit\":\"Co-Authored-By\",\"pr\":\"\"}}"));
    }

    @Test
    void equalFragmentChangesNothing() {
        var result = SettingsFragmentMerger.merge(json("{\"a\":{\"b\":1}}"), json("{\"a\":{\"b\":1}}"));

        assertThat(result.changed()).isFalse();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void mismatchedKindsAreAConflict() {
        assertThat(SettingsFragmentMerger.merge(json("{\"a\":1}"), json("{\"a\":{\"b\":1}}")).conflicts()).containsExactly("a");
    }

    @Test
    void createsAMissingFileAndItsDirectory() {
        var file = directory.resolve("config").resolve("settings.json");

        var result = SettingsFragmentMerger.mergeFile(file, json("{\"attribution\":{\"commit\":\"\"}}"));

        assertThat(result.changed()).isTrue();
        assertThat(json(read(file))).isEqualTo(json("{\"attribution\":{\"commit\":\"\"}}"));
    }

    @Test
    void mergesIntoAnExistingFileKeepingItsOtherSettings() throws Exception {
        var file = Files.writeString(directory.resolve("settings.json"), "{\"model\":\"opus\"}");

        SettingsFragmentMerger.mergeFile(file, json("{\"attribution\":{\"commit\":\"\"}}"));

        assertThat(json(read(file))).isEqualTo(json("{\"model\":\"opus\",\"attribution\":{\"commit\":\"\"}}"));
    }

    @Test
    void conflictingFileIsLeftExactlyAsItWas() throws Exception {
        var original = "{\"attribution\":{\"commit\":\"mine\"}}";
        var file = Files.writeString(directory.resolve("settings.json"), original);

        var result = SettingsFragmentMerger.mergeFile(file, json("{\"attribution\":{\"commit\":\"\"}}"));

        assertThat(result.conflicts()).containsExactly("attribution.commit");
        assertThat(read(file)).isEqualTo(original);
    }

    @Test
    void invalidExistingJsonIsReportedAndNeverOverwritten() throws Exception {
        var file = Files.writeString(directory.resolve("settings.json"), "{ not json");

        assertThatThrownBy(() -> SettingsFragmentMerger.mergeFile(file, json("{\"a\":1}"))).isInstanceOf(ExtensionException.class);

        assertThat(read(file)).isEqualTo("{ not json");
        assertThat(directory).isDirectoryContaining(path -> path.getFileName().toString().equals("settings.json"));
        try (var files = Files.list(directory)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void nonObjectRootIsReported() throws Exception {
        var file = Files.writeString(directory.resolve("settings.json"), "[1]");

        assertThatThrownBy(() -> SettingsFragmentMerger.mergeFile(file, json("{\"a\":1}"))).isInstanceOf(ExtensionException.class);
        assertThat(read(file)).isEqualTo("[1]");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
