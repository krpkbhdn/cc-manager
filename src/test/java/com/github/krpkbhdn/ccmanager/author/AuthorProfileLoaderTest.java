package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuthorProfileLoaderTest {

    final AuthorProfileLoader loader = new AuthorProfileLoader();

    @Test
    void bundledProfileIsValid() {
        var profile = loader.profile();

        assertThat(profile.version(AuthorComponent.USER_SETUP)).isGreaterThanOrEqualTo(1);
        assertThat(profile.userSetup().extensions().plugins()).contains("ponytail@ponytail");
        assertThat(profile.userSetup().extensions().skills()).extracting(ProfileSkill::name).containsExactly("jscpd", "dry-refactoring");
    }

    @Test
    void everyListedFileIsReadable() {
        loader.profile().files().forEach(file -> assertThat(loader.text(file)).isNotBlank());
    }

    @Test
    void bundledDevTeamHasExactlyOneLead() {
        var team = loader.teamTemplates().getFirst();

        assertThat(team.name()).isEqualTo("Dev team");
        assertThat(team.roles()).extracting(role -> role.name()).doesNotHaveDuplicates();
        assertThat(team.roles()).filteredOn(role -> role.lead()).hasSize(1);
    }

    static AuthorProfileLoader loaderOf(String files, String skills) {
        var json = """
                {"files": %s, "commonPrompt": "common/session-prompt.md",
                 "userSetup": {"version": 1, "title": "U", "summary": "u", "prompt": "common/session-prompt.md",
                               "extensions": {"skills": %s}},
                 "projectSetup": {"version": 1, "title": "P", "summary": "p", "prompt": "common/session-prompt.md"},
                 "teams": {"version": 1, "title": "T", "summary": "t"}}
                """.formatted(files, skills);
        return new AuthorProfileLoader(AuthorProfile.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void minimalManifestIsAccepted() {
        assertThat(loaderOf("[\"common/session-prompt.md\"]", "[]").profile().version(AuthorComponent.USER_SETUP)).isEqualTo(1);
    }

    @Test
    void unsafePathIsRejected() {
        assertThatThrownBy(() -> loaderOf("[\"../x\"]", "[]")).hasMessageContaining("unsafe path");
    }

    @Test
    void referencedFileMissingFromFilesIsRejected() {
        assertThatThrownBy(() -> loaderOf("[]", "[]")).hasMessageContaining("is not listed in files");
    }

    @Test
    void nonKebabCaseSkillNameIsRejected() {
        assertThatThrownBy(() -> loaderOf("[\"common/session-prompt.md\"]", "[{\"name\": \"Bad_Name\", \"files\": [\"SKILL.md\"]}]"))
                .hasMessageContaining("kebab-case");
    }

    @Test
    void skillWithoutSkillMdIsRejected() {
        assertThatThrownBy(() -> loaderOf("[\"common/session-prompt.md\"]", "[{\"name\": \"empty\", \"files\": []}]"))
                .hasMessageContaining("needs SKILL.md");
    }

    @Test
    void missingResourceIsRejected() {
        assertThatThrownBy(() -> loader.open("nope.md")).isInstanceOf(IllegalArgumentException.class);
    }
}
