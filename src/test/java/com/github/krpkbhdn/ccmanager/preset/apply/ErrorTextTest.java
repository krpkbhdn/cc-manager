package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ErrorTextTest {

    private static final String LEAKY = "clone https://user:hunter2@host.example/x?token=abc failed, API_KEY=sk-verysecret";

    @Test
    void urlCredentialsAndSecretValuesAreMasked() {
        var text = ErrorText.redacted(new IllegalStateException(LEAKY));

        assertThat(text).contains("host.example").doesNotContain("hunter2", "abc", "sk-verysecret");
    }

    @Test
    void blankMessageFallsBackToClassName() {
        assertThat(ErrorText.redacted(new IllegalStateException(" "))).isEqualTo("IllegalStateException");
    }

    @Test
    void longMessageIsTruncated() {
        assertThat(ErrorText.redacted(new RuntimeException("x".repeat(1000)))).hasSizeLessThan(400);
    }

    @Test
    void planAndApplyFailuresCarryOnlyRedactedText() {
        var component = new PresetComponent(1, PresetComponentKind.FILE, "f", JsonMapper.builder().build().readTree("{}"));
        var presets = mock(ProjectPresetService.class);
        when(presets.get(1L)).thenReturn(Optional.of(new ProjectPreset(1, "p", "", null, false, false, 1, List.of(component))));
        var service = new PresetApplyService(presets, mock(PresetApplicationService.class), List.of(new FailingApplier()));

        var plan = service.plan(1, Path.of("."));
        var result = service.apply(1, 1, Path.of("."), Set.of(1L));

        assertThat(plan.getFirst().detail()).contains("Could not check").doesNotContain("hunter2", "abc", "sk-verysecret");
        assertThat(result.outcomes().getFirst().message()).isNotBlank().doesNotContain("hunter2", "abc", "sk-verysecret");
        assertThat(result.outcomes().getFirst().status()).isEqualTo(OutcomeStatus.FAILED);
    }

    private static final class FailingApplier implements PresetComponentApplier {
        private boolean planFails = true;

        @Override
        public PresetComponentKind kind() {
            return PresetComponentKind.FILE;
        }

        @Override
        public PlannedChange plan(PresetComponent component, Path projectDir) throws IOException {
            if (planFails) {
                planFails = false;
                throw new IOException(LEAKY);
            }
            return PlannedChange.of(component, ChangeAction.CREATE, "ok");
        }

        @Override
        public ComponentOutcome apply(PresetComponent component, Path projectDir) throws IOException {
            throw new IOException(LEAKY);
        }
    }
}
