package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(team.roles()).extracting(role -> role.name()).containsExactly("architect", "developer", "tester");
    }

    @Test
    void missingResourceIsRejected() {
        assertThatThrownBy(() -> loader.open("nope.md")).isInstanceOf(IllegalArgumentException.class);
    }
}
