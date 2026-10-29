package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class VoiceConfigurationBootTest {

    @Test
    void contextCreatesVoiceBeans(@TempDir Path dataDir) {
        try (var context = new SpringApplicationBuilder(CcManagerApplication.class).web(WebApplicationType.NONE)
                .headless(true).run("--cc-manager.data-dir=" + dataDir.toAbsolutePath())) {
            assertThat(context.getBean(VoiceProperties.class).threads()).isPositive();
            assertThat(context.getBean(VoiceInputService.class)).isNotNull();
        }
    }
}
