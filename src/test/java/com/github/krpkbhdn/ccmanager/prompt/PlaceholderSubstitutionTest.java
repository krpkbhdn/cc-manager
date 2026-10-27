package com.github.krpkbhdn.ccmanager.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaceholderSubstitutionTest {

    @Test
    void replacesEveryKnownPlaceholderAndRepeats() {
        var values = new PlaceholderValues("app", "main", "A.java");

        assertThat(PlaceholderSubstitution.apply("{project}/{branch}: fix {file} in {project}", values))
                .isEqualTo("app/main: fix A.java in app");
    }

    @Test
    void unknownPlaceholdersAndMissingValuesStayLiteral() {
        var values = new PlaceholderValues("app", null, null);

        assertThat(PlaceholderSubstitution.apply("{project} {branch} {file} {other} {}", values))
                .isEqualTo("app {branch} {file} {other} {}");
    }

    @Test
    void aValueContainingAnotherPlaceholderIsNotExpandedAgain() {
        var values = new PlaceholderValues("{branch}", "{file}", "{project}");

        assertThat(PlaceholderSubstitution.apply("{project} {branch} {file}", values)).isEqualTo("{branch} {file} {project}");
    }

    @Test
    void bracesInValuesAndAroundPlaceholdersSurvive() {
        var values = new PlaceholderValues("a{b}", "x", "y");

        assertThat(PlaceholderSubstitution.apply("{{project}} {project", values)).isEqualTo("{a{b}} {project");
    }

    @Test
    void placeholdersAreCaseSensitiveAndEmptyValuesReplaceWithNothing() {
        var values = new PlaceholderValues("app", "main", "");

        assertThat(PlaceholderSubstitution.apply("{Project} a{file}b", values)).isEqualTo("{Project} ab");
        assertThat(PlaceholderSubstitution.apply("", values)).isEmpty();
    }

    @Test
    void valuesWithRegexSpecialCharactersAreInsertedAsIs() {
        assertThat(PlaceholderSubstitution.apply("{project}", new PlaceholderValues("a$1\\b", null, null))).isEqualTo("a$1\\b");
    }
}
