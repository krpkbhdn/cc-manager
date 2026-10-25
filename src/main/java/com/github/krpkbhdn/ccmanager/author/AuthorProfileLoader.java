package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Loads and checks the bundled author profile at startup; a broken profile is a build defect, so it fails fast. */
@Component
public class AuthorProfileLoader {

    /** Resource directories of the user and project scope. */
    public static final String USER_DIRECTORY = "user";
    public static final String PROJECT_DIRECTORY = "project";

    private static final String ROOT = "/author-profile/";
    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final AuthorProfile profile;
    private final List<TeamTemplate> teamTemplates;

    public AuthorProfileLoader() {
        try {
            this.profile = readProfile();
            validateManifest();
            this.teamTemplates = loadTeamTemplates();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Bundled author profile is invalid: " + e.getMessage(), e);
        }
    }

    public AuthorProfile profile() {
        return profile;
    }

    public List<TeamTemplate> teamTemplates() {
        return teamTemplates;
    }

    /** @throws IllegalArgumentException when the resource is missing */
    public String text(String path) {
        try (var in = open(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @throws IllegalArgumentException when the resource is missing */
    public InputStream open(String path) {
        var in = AuthorProfileLoader.class.getResourceAsStream(ROOT + path);
        if (in == null) {
            throw new IllegalArgumentException("missing resource " + path);
        }
        return in;
    }

    private AuthorProfile readProfile() throws IOException {
        try (var in = open("profile.json")) {
            return AuthorProfile.parse(in);
        }
    }

    private void validateManifest() throws IOException {
        for (var component : AuthorComponent.values()) {
            require(profile.version(component) >= 1, component + " version must be at least 1");
        }
        for (var path : profile.files()) {
            require(!path.isBlank() && !path.contains("..") && !path.startsWith("/"), "unsafe path: " + path);
            open(path).close();
        }
        var referenced = new ArrayList<String>();
        referenced.add(profile.commonPrompt());
        referenced.add(profile.userSetup().prompt());
        referenced.add(profile.projectSetup().prompt());
        referenced.addAll(profile.teams().templates());
        referenced.addAll(skillFiles(profile.userSetup(), USER_DIRECTORY));
        referenced.addAll(skillFiles(profile.projectSetup(), PROJECT_DIRECTORY));
        referenced.forEach(path -> require(profile.files().contains(path), path + " is not listed in files"));
    }

    private static List<String> skillFiles(ScopeSetup scope, String directory) {
        var paths = new ArrayList<String>();
        for (var skill : scope.extensions().skills()) {
            require(skill.name() != null && KEBAB_CASE.matcher(skill.name()).matches(), "skill name must be kebab-case: " + skill.name());
            skill.files().forEach(file -> paths.add(directory + "/skills/" + skill.name() + "/" + file));
        }
        return paths;
    }

    private List<TeamTemplate> loadTeamTemplates() throws IOException {
        var templates = new ArrayList<TeamTemplate>();
        for (var path : profile.teams().templates()) {
            try (var in = open(path)) {
                var template = MAPPER.readValue(in, TeamTemplate.class);
                TeamService.validate(template.name(), template.roles());
                templates.add(template);
            }
        }
        return List.copyOf(templates);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
