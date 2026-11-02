package com.github.krpkbhdn.ccmanager.git;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings of the claude one-shot that describes changes and of the git reads feeding it. */
@ConfigurationProperties("cc-manager.git.description")
public record ChangeDescriptionProperties(
        @DefaultValue("haiku") String model,
        @DefaultValue("120s") Duration timeout,
        @DefaultValue("30000") int contextChars,
        @DefaultValue("4000") int untrackedFileChars) {

    public ChangeDescriptionProperties {
        positive("cc-manager.git.description.timeout", timeout);
        positive("cc-manager.git.description.context-chars", contextChars);
        positive("cc-manager.git.description.untracked-file-chars", untrackedFileChars);
    }
}
