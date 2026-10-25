package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;
import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthorSessionPromptTest {

    @TempDir
    Path temp;

    AuthorSessionPrompt prompt;
    Path project;

    @BeforeEach
    void setUp() {
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, temp.resolve("home"));
        prompt = new AuthorSessionPrompt(new AppProperties(temp.resolve("data")), new AuthorProfileLoader(), host);
        project = temp.resolve("work");
    }

    String promptText(SessionHandoff handoff) throws IOException {
        var message = handoff.firstMessage();
        return Files.readString(Path.of(message.substring("Read ".length(), message.length() - " and carry out its instructions.".length())));
    }

    @Test
    void stagesEveryProfileFile() {
        prompt.prepare(Set.of(AuthorComponent.USER_SETUP), project, List.of());

        assertThat(prompt.profileDirectory().resolve("user/skills/jscpd/SKILL.md")).isRegularFile();
        assertThat(prompt.profileDirectory().resolve("project/jscpd.json")).isRegularFile();
    }

    @Test
    void firstMessageIsShortAndFreeOfShellMetacharacters() {
        var handoff = prompt.prepare(Set.of(AuthorComponent.USER_SETUP), project, List.of());

        assertThat(handoff.firstMessage()).startsWith("Read ").endsWith(" and carry out its instructions.")
                .contains(prompt.profileDirectory().resolve("session-prompt-").toString())
                .doesNotContainPattern("[&|<>^%]");
        assertThat(handoff.profileDirectory()).isEqualTo(prompt.profileDirectory());
    }

    @Test
    void userPromptCoversUserSectionOnlyAndFillsPlaceholders() throws IOException {
        var handoff = prompt.prepare(Set.of(AuthorComponent.USER_SETUP), project, List.of());
        var text = promptText(handoff);

        assertThat(text).contains("## User setup").doesNotContain("## Project setup").doesNotContain("{{");
        assertThat(text).contains(temp.resolve("home").resolve(".claude").toString());
    }

    @Test
    void combinedPromptHasBothSectionsAndTheProjectPath() throws IOException {
        var handoff = prompt.prepare(Set.of(AuthorComponent.USER_SETUP, AuthorComponent.PROJECT_SETUP), project, List.of());
        var text = promptText(handoff);

        assertThat(text).contains("## User setup").contains("## Project setup").contains(project.toString());
    }

    @Test
    void promptTellsClaudeToExamineAskBeforeChangingAndNeverOverwrite() throws IOException {
        var handoff = prompt.prepare(Set.of(AuthorComponent.PROJECT_SETUP), project, List.of());
        var text = promptText(handoff);

        assertThat(text).contains("Examine first").contains("Never overwrite blindly").contains("go-ahead")
                .contains("pip install graphifyy").contains("graphify install").contains("graphify hook install")
                .contains("npm i -g jscpd").contains("costs tokens");
    }

    @Test
    void preInstallReportListsTheOutcomes() throws IOException {
        var outcome = new InstallOutcome(List.of("plugin ponytail@ponytail"), List.of(), List.of("plugin x@y"), List.of(), List.of());
        var handoff = prompt.prepare(Set.of(AuthorComponent.USER_SETUP), project, List.of(new ComponentOutcome(AuthorComponent.USER_SETUP, outcome)));

        assertThat(promptText(handoff)).contains("- Installed plugin ponytail@ponytail").contains("plugin x@y");
    }

    @Test
    void everyPrepareWritesItsOwnPromptFile() throws IOException {
        var first = prompt.prepare(Set.of(AuthorComponent.USER_SETUP), project, List.of());
        var second = prompt.prepare(Set.of(AuthorComponent.PROJECT_SETUP), project, List.of());

        assertThat(first.firstMessage()).isNotEqualTo(second.firstMessage());
        assertThat(promptText(first)).doesNotContain("## Project setup");
        assertThat(promptText(second)).contains("## Project setup");
    }
}
