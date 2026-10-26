package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;
import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stages the bundled profile in the data directory and writes the prompt Claude follows to finish the setup. The first
 * message only points at the prompt file, so it stays short and free of shell metacharacters.
 */
@Component
public class AuthorSessionPrompt {

    private static final String PROMPT_FILE_PREFIX = "session-prompt-";
    private static final String PREINSTALL_REPORT = "{{preInstallReport}}";
    private static final String PROFILE_DIR = "{{profileDir}}";
    private static final String PROJECT_PATH = "{{projectPath}}";
    private static final String CLAUDE_CONFIG_DIR = "{{claudeConfigDir}}";

    private final Path directory;
    private final AuthorProfileLoader loader;
    private final HostEnvironment host;

    public AuthorSessionPrompt(AppProperties properties, AuthorProfileLoader loader, HostEnvironment host) {
        this.directory = properties.dataDir().resolve("author-profile");
        this.loader = loader;
        this.host = host;
    }

    /** The directory passed to claude as {@code --add-dir}. */
    public Path profileDirectory() {
        return directory;
    }

    /** @param claudeComponents the components Claude finishes: USER_SETUP and/or PROJECT_SETUP, others are ignored */
    public SessionHandoff prepare(Set<AuthorComponent> claudeComponents, Path projectPath, List<ComponentOutcome> outcomes) {
        var profile = loader.profile();
        profile.files().forEach(this::stage);
        var sections = new ArrayList<String>();
        sections.add(loader.text(profile.commonPrompt()));
        if (claudeComponents.contains(AuthorComponent.USER_SETUP)) {
            sections.add(loader.text(profile.userSetup().prompt()));
        }
        if (claudeComponents.contains(AuthorComponent.PROJECT_SETUP)) {
            sections.add(loader.text(profile.projectSetup().prompt()));
        }
        var prompt = String.join("\n\n", sections)
                .replace(PREINSTALL_REPORT, report(outcomes))
                .replace(PROFILE_DIR, directory.toString())
                .replace(PROJECT_PATH, projectPath.toString())
                .replace(CLAUDE_CONFIG_DIR, host.claudeConfigDirectory().toString());
        var promptFile = directory.resolve(PROMPT_FILE_PREFIX + UUID.randomUUID() + ".md");
        write(promptFile, prompt);
        return new SessionHandoff("Read " + promptFile + " and carry out its instructions.", directory);
    }

    private static String report(List<ComponentOutcome> outcomes) {
        var merged = outcomes.stream().map(ComponentOutcome::outcome).reduce(InstallOutcome.NONE, InstallOutcome::merge);
        var lines = merged.report();
        return lines.isEmpty() ? "Nothing was installed by the app." : String.join("\n", lines.stream().map(line -> "- " + line).toList());
    }

    private void stage(String file) {
        try (var in = loader.open(file)) {
            var target = directory.resolve(file);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot stage profile file " + file, e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
