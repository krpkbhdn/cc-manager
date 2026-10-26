package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuthorProfileTest {

    static AuthorProfile parse(String json) {
        return AuthorProfile.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesScopesAndTeamsAndDefaultsMissingListsToEmpty() {
        var profile = parse("""
                {"files": ["a.md"], "commonPrompt": "a.md",
                 "userSetup": {"version": 2, "title": "User", "summary": "u", "prompt": "a.md",
                   "extensions": {"plugins": ["x@y"], "mcpServers": [{"name": "m", "config": {"type": "http", "url": "https://h"}}]}},
                 "projectSetup": {"version": 3, "title": "Project", "summary": "p", "prompt": "a.md"},
                 "teams": {"version": 4, "title": "Teams", "summary": "t", "templates": ["t.json"]}}
                """);

        assertThat(profile.userSetup().extensions().plugins()).containsExactly("x@y");
        assertThat(profile.userSetup().extensions().mcpServers().getFirst().config().path("url").asString()).isEqualTo("https://h");
        assertThat(profile.userSetup().extensions().skills()).isEmpty();
        assertThat(profile.projectSetup().extensions().plugins()).isEmpty();
        assertThat(profile.teams().templates()).containsExactly("t.json");
    }

    @Test
    void componentAccessorsPickTheMatchingSection() {
        var profile = parse("""
                {"files": [], "commonPrompt": "a",
                 "userSetup": {"version": 2, "title": "User", "summary": "u", "prompt": "a"},
                 "projectSetup": {"version": 3, "title": "Project", "summary": "p", "prompt": "a"},
                 "teams": {"version": 4, "title": "Teams", "summary": "t"}}
                """);

        assertThat(profile.version(AuthorComponent.USER_SETUP)).isEqualTo(2);
        assertThat(profile.version(AuthorComponent.PROJECT_SETUP)).isEqualTo(3);
        assertThat(profile.version(AuthorComponent.TEAMS)).isEqualTo(4);
        assertThat(profile.title(AuthorComponent.PROJECT_SETUP)).isEqualTo("Project");
        assertThat(profile.summary(AuthorComponent.TEAMS)).isEqualTo("t");
    }

    @Test
    void rejectsTextThatIsNotJson() {
        assertThatThrownBy(() -> parse("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
