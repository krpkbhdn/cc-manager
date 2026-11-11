package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SettingsApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private final SettingsApplier applier = new SettingsApplier();

    private static PresetComponent settings(String fragmentJson) {
        return new PresetComponent(1, PresetComponentKind.SETTINGS, "s", JSON.createObjectNode().set("fragment", JSON.readTree(fragmentJson)));
    }

    private void existing(String json) throws Exception {
        Files.createDirectories(dir.resolve(".claude"));
        Files.writeString(dir.resolve(".claude/settings.json"), json);
    }

    private JsonNode written() throws Exception {
        return JSON.readTree(Files.readString(dir.resolve(".claude/settings.json")));
    }

    @Test
    void missingFileIsCreatedFromTheFragment() throws Exception {
        var component = settings("{\"model\":\"opus\"}");

        assertThat(applier.plan(component, dir).action()).isEqualTo(ChangeAction.CREATE);
        applier.apply(component, dir);

        assertThat(written().path("model").asString()).isEqualTo("opus");
    }

    @Test
    void existingKeysArePreservedWhenTheFragmentIsMerged() throws Exception {
        existing("{\"theme\":\"dark\",\"attribution\":{\"A\":\"1\"}}");
        var component = settings("{\"attribution\":{\"B\":\"2\"},\"model\":\"opus\"}");

        assertThat(applier.plan(component, dir).action()).isEqualTo(ChangeAction.UPDATE);
        applier.apply(component, dir);

        var json = written();
        assertThat(json.path("theme").asString()).isEqualTo("dark");
        assertThat(json.path("attribution").path("A").asString()).isEqualTo("1");
        assertThat(json.path("attribution").path("B").asString()).isEqualTo("2");
        assertThat(json.path("model").asString()).isEqualTo("opus");
    }

    @Test
    void fragmentWithCodeRunningKeysNeedsConfirmation() throws Exception {
        var change = applier.plan(settings("{\"hooks\":{\"Stop\":[]},\"model\":\"opus\"}"), dir);

        assertThat(change.action()).isEqualTo(ChangeAction.NEEDS_CONFIRMATION);
        assertThat(change.detail()).contains("hooks").doesNotContain("model");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fileSuggestion", "enabledMcpjsonServers", "disableAllHooks", "allowedMcpServers"})
    void trustAndCommandKeysNeedConfirmation(String key) throws Exception {
        var change = applier.plan(settings("{\"" + key + "\":{},\"model\":\"opus\"}"), dir);

        assertThat(change.action()).isEqualTo(ChangeAction.NEEDS_CONFIRMATION);
        assertThat(change.detail()).contains(key);
    }

    @Test
    void aKeyWithADifferentExistingValueIsNotOverwritten() throws Exception {
        existing("{\"model\":\"sonnet\"}");
        var component = settings("{\"model\":\"opus\"}");

        assertThat(applier.plan(component, dir).action()).isEqualTo(ChangeAction.CONFLICT);
        applier.apply(component, dir);

        assertThat(written().path("model").asString()).isEqualTo("sonnet");
    }

    @Test
    void alreadyContainedValuesArePresent() throws Exception {
        existing("{\"model\":\"opus\",\"x\":1}");

        assertThat(applier.plan(settings("{\"model\":\"opus\"}"), dir).action()).isEqualTo(ChangeAction.PRESENT);
    }

    @Test
    void invalidExistingJsonIsReportedAndLeftUntouched() throws Exception {
        existing("{ not json");

        assertThatThrownBy(() -> applier.plan(settings("{\"a\":1}"), dir)).isInstanceOf(RuntimeException.class);
        assertThat(Files.readString(dir.resolve(".claude/settings.json"))).isEqualTo("{ not json");
    }
}
