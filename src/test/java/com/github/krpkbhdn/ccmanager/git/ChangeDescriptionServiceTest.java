package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ClaudeCli;
import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ChangeDescriptionServiceTest {

    private final ClaudeCli claude = mock(ClaudeCli.class);
    private final ChangeDescriptionService service = new ChangeDescriptionService(claude,
            new ChangeDescriptionProperties("haiku", Duration.ofSeconds(1), Duration.ofSeconds(10), 20000, 100));

    private static CommandRunner.Result answer(String result) {
        return new CommandRunner.Result(0, "{\"result\":" + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result) + "}", "", false);
    }

    private GitCliSandbox featureBranchWithEdits(Path root) {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("README.md", "hello\n");
        sandbox.commitAll("initial");
        sandbox.git("checkout", "-b", "task/add-login");
        sandbox.write("Login.java", "class Login {}\n");
        sandbox.commitAll("add login class");
        sandbox.write("README.md", "hello\nlogin\n");
        sandbox.write("notes.txt", "untracked notes\n");
        sandbox.write(".env", "TOKEN=hunter2\n");
        sandbox.writeBytes("logo.png", new byte[] {1, 0, 2, 0});
        return sandbox;
    }

    @Test
    void usesValidClaudeAnswer(@TempDir Path root) {
        var sandbox = featureBranchWithEdits(root);
        when(claude.execute(any(), any(), any())).thenReturn(answer("{\"title\":\"Add login\",\"body\":\"- one\"}"));

        assertThat(service.describe(sandbox.repo, "main", "")).isEqualTo(new ChangeDescription("Add login", "- one"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void promptHasUncommittedUntrackedAndCommittedChangesButNoSecretsOrBinaries(@TempDir Path root) {
        var sandbox = featureBranchWithEdits(root);
        when(claude.execute(any(), any(), any())).thenReturn(answer("{\"title\":\"Add login\",\"body\":\"\"}"));

        service.describe(sandbox.repo, "main", "implement login");

        var arguments = ArgumentCaptor.forClass(List.class);
        verify(claude).execute(any(), any(), arguments.capture());
        var prompt = ((List<String>) arguments.getValue()).get(1);
        assertThat(arguments.getValue()).containsSubsequence("--tools", "").doesNotContain("--allowedTools");
        assertThat(prompt).contains("+login", "class Login", "add login class", "untracked notes", "implement login")
                .contains("--- new file: .env (content not shown)", "--- new file: logo.png (content not shown)")
                .doesNotContain("hunter2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void trackedSecretsAreExcludedFromTheDiffAndSymlinksAreNotRead(@TempDir Path root) throws java.io.IOException {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("README.md", "hello\n");
        sandbox.commitAll("initial");
        sandbox.git("checkout", "-b", "feature");
        sandbox.write("app.tfvars", "password=hunter2\n");
        sandbox.write("config/.npmrc", "_authToken=hunter3\n");
        sandbox.commitAll("add vars");
        sandbox.write("app.tfvars", "password=hunter4\n");
        Files.writeString(root.resolve("outside.txt"), "outside-secret");
        try {
            Files.createSymbolicLink(sandbox.repo.resolve("link.txt"), root.resolve("outside.txt"));
        } catch (java.io.IOException | UnsupportedOperationException e) {
            // symlinks need a privilege on some systems; the secret checks still run
        }
        when(claude.execute(any(), any(), any())).thenReturn(answer("{\"title\":\"Add vars\",\"body\":\"\"}"));

        service.describe(sandbox.repo, "main", "");

        var arguments = ArgumentCaptor.forClass(List.class);
        verify(claude).execute(any(), any(), arguments.capture());
        var prompt = (String) ((List<String>) arguments.getValue()).get(1);
        assertThat(prompt).doesNotContain("hunter", "outside-secret").contains("--- changed file: app.tfvars (content not shown)", "--- changed file: config/.npmrc");
        assertThat(arguments.getValue()).contains("--no-session-persistence", "--strict-mcp-config", "--disable-slash-commands");
    }

    @Test
    @SuppressWarnings("unchecked")
    void hugeDiffAndQuotesStayWithinTheCommandLineLimit(@TempDir Path root) {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("initial");
        sandbox.git("checkout", "-b", "feature");
        sandbox.write("a.txt", "\"q\"\\\n".repeat(50_000));
        var bigService = new ChangeDescriptionService(claude, new ChangeDescriptionProperties("haiku", Duration.ofSeconds(1), Duration.ofSeconds(10), 200_000, 100));
        when(claude.execute(any(), any(), any())).thenReturn(answer("{\"title\":\"Edit a\",\"body\":\"\"}"));

        bigService.describe(sandbox.repo, "main", "t\"".repeat(5000));

        var arguments = ArgumentCaptor.forClass(List.class);
        verify(claude).execute(any(), any(), arguments.capture());
        var prompt = (String) ((List<String>) arguments.getValue()).get(1);
        assertThat(prompt.length() + prompt.chars().filter(c -> c == '"' || c == '\\').count()).isLessThan(20_000);
    }

    @Test
    void interruptedThreadDoesNotCallClaudeAgain(@TempDir Path root) {
        var sandbox = featureBranchWithEdits(root);
        when(claude.execute(any(), any(), any())).thenAnswer(call -> {
            Thread.currentThread().interrupt();
            return new CommandRunner.Result(-1, "", "", true);
        });

        var description = service.describe(sandbox.repo, "main", "");
        var interrupted = Thread.interrupted();

        verify(claude, times(1)).execute(any(), any(), any());
        assertThat(interrupted).isTrue();
        assertThat(description.title()).isEqualTo("Add login");
    }

    @Test
    void invalidBodyFallsBackToTheDeterministicBodyKeepingTheTitle(@TempDir Path root) {
        var sandbox = featureBranchWithEdits(root);
        when(claude.execute(any(), any(), any())).thenReturn(answer("{\"title\":\"Add login\",\"body\":\"I'm sorry, I cannot describe this.\"}"));

        var description = service.describe(sandbox.repo, "main", "");

        assertThat(description.title()).isEqualTo("Add login");
        assertThat(description.body()).startsWith("- add login class");
    }

    @Test
    void validTitlesWithRefusalWordsInsideAreAccepted() {
        assertThat(ChangeDescriptionAnswer.validTitle("Handle unable to connect error")).contains("Handle unable to connect error");
        assertThat(ChangeDescriptionAnswer.validTitle("Fix cannot find module")).contains("Fix cannot find module");
        assertThat(ChangeDescriptionAnswer.validTitle("I cannot do that")).isEmpty();
        assertThat(ChangeDescriptionAnswer.validTitle("Sorry, nothing")).isEmpty();
    }

    @Test
    void bodyIsCleanedAndLimited() {
        assertThat(ChangeDescriptionAnswer.validBody("<!-- hidden -->\n```markdown\n- a\n```")).isEqualTo("- a");
        assertThat(ChangeDescriptionAnswer.validBody("x".repeat(ChangeDescriptionAnswer.MAX_BODY + 1))).isEmpty();
        assertThat(ChangeDescriptionAnswer.validBody("Unfortunately there is nothing")).isEmpty();
        assertThat(ChangeDescriptionAnswer.validBody("- fixes cannot happen again")).isEqualTo("- fixes cannot happen again");
    }

    @Test
    void badAnswersRetryOnceThenFallBackToTaskTitle(@TempDir Path root) {
        var sandbox = featureBranchWithEdits(root);
        when(claude.execute(any(), any(), any())).thenReturn(answer("I'm sorry, I cannot write a description because there are no commits."));

        var description = service.describe(sandbox.repo, "main", "fix the login redirect.\nmore text");

        verify(claude, times(2)).execute(any(), any(), any());
        assertThat(description.title()).isEqualTo("Fix the login redirect");
        assertThat(description.body()).startsWith("- add login class").contains("`Login.java`", "`notes.txt`");
    }

    @Test
    void refusalProseMultilineTitleAndErrorAreRejected() {
        assertThat(ChangeDescriptionAnswer.parseResult("Sure, here is the title")).isEmpty();
        assertThat(ChangeDescriptionAnswer.parseResult("{\"title\":\"Add a\\nb\",\"body\":\"\"}")).isEmpty();
        assertThat(ChangeDescriptionAnswer.parseResult("{\"title\":\"I can't describe this\",\"body\":\"\"}")).isEmpty();
        assertThat(ChangeDescriptionAnswer.parseResult("{\"title\":\"" + "x".repeat(73) + "\",\"body\":\"\"}")).isEmpty();
        assertThat(ChangeDescriptionAnswer.parseResult("NONE")).isEmpty();
        assertThat(ChangeDescriptionAnswer.parse("{\"is_error\":true,\"result\":\"{\\\"title\\\":\\\"Fine\\\"}\"}")).isEmpty();
        assertThat(ChangeDescriptionAnswer.parse("not json")).isEmpty();
    }

    @Test
    void titleDecorationIsStripped() {
        assertThat(ChangeDescriptionAnswer.parseResult("{\"title\":\"Title: \\\"Add login\\\"\",\"body\":\" b \"}")).contains(new ChangeDescription("Add login", "b"));
        assertThat(ChangeDescriptionAnswer.parseResult("```json\n{\"title\":\"# Add login\"}\n```")).contains(new ChangeDescription("Add login", ""));
    }

    @Test
    void timeoutAndExceptionFallBack(@TempDir Path root) {
        var sandbox = featureBranchWithEdits(root);
        when(claude.execute(any(), any(), any())).thenReturn(new CommandRunner.Result(-1, "", "", true)).thenThrow(new ExtensionException("boom"));

        assertThat(service.describe(sandbox.repo, "main", "").title()).isEqualTo("Add login");
    }

    @Test
    void withoutChangesClaudeIsNotAskedAndFallbackIsUsed(@TempDir Path root) {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("initial");

        assertThat(service.describe(sandbox.repo, "main", "").title()).isEqualTo("Update");
        assertThat(service.hasChanges(sandbox.repo, "main")).isFalse();
        verify(claude, times(0)).execute(any(), any(), any());
    }

    @Test
    void hasChangesSeesUntrackedUncommittedAndCommittedWork(@TempDir Path root) {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("initial");
        sandbox.git("checkout", "-b", "feature");
        assertThat(service.hasChanges(sandbox.repo, "main")).isFalse();
        sandbox.write("new.txt", "x\n");
        assertThat(service.hasChanges(sandbox.repo, "main")).isTrue();
        sandbox.commitAll("add new");
        assertThat(service.hasChanges(sandbox.repo, "main")).isTrue();
        assertThat(service.hasChanges(sandbox.repo, "--output=leak")).isFalse();
    }

    @Test
    void fallbackOrderIsTaskThenBranchThenNewestSubjectThenGeneric() {
        var subjects = List.of("newest", "oldest");

        assertThat(ChangeDescriptionFallback.of("do it", "task/x", subjects, List.of()).title()).isEqualTo("Do it");
        assertThat(ChangeDescriptionFallback.of("  ", "task/add-login", subjects, List.of()).title()).isEqualTo("Add login");
        assertThat(ChangeDescriptionFallback.of("", "develop", subjects, List.of()).title()).isEqualTo("newest");
        assertThat(ChangeDescriptionFallback.of("", "HEAD", List.of(), List.of("a.txt")))
                .isEqualTo(new ChangeDescription("Update", "Changed files:\n- `a.txt`"));
    }

    @Test
    void propertiesBindUnderTheDescriptionPrefix() {
        var source = new MapConfigurationPropertySource(java.util.Map.of("cc-manager.git.description.model", "sonnet", "cc-manager.git.description.context-chars", "5"));
        var bound = new Binder(source).bindOrCreate("cc-manager.git.description", ChangeDescriptionProperties.class);

        assertThat(bound.model()).isEqualTo("sonnet");
        assertThat(bound.contextChars()).isEqualTo(5);
        assertThat(bound.timeout()).isEqualTo(Duration.ofSeconds(120));
    }
}
