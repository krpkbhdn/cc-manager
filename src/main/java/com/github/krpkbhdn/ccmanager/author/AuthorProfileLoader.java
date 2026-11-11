package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Loads and checks the bundled author catalog (schema v2) at startup; a broken profile is a build defect, so it fails fast. */
@Component
public class AuthorProfileLoader {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String ROOT = "/author-profile/";
    private static final String SKILL_FILE = "SKILL.md";
    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final AuthorCatalog catalog;
    private final Map<String, TeamTemplate> teamTemplates;

    public AuthorProfileLoader() {
        this(readBundledCatalog());
    }

    AuthorProfileLoader(AuthorCatalog catalog) {
        try {
            this.catalog = catalog;
            validate();
            this.teamTemplates = loadTeamTemplates();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Bundled author profile is invalid: " + e.getMessage(), e);
        }
    }

    public AuthorCatalog catalog() {
        return catalog;
    }

    /** The template of a TEAM item; validated at startup, so it is present for every such item. */
    public TeamTemplate teamTemplate(String itemId) {
        return teamTemplates.get(itemId);
    }

    /** The skill of a SKILL item with the files the profile ships inside its directory. */
    public ProfileSkill skill(AuthorItem item) {
        var prefix = item.path() + "/";
        var files = catalog.files().stream().filter(file -> file.startsWith(prefix)).map(file -> file.substring(prefix.length())).toList();
        return new ProfileSkill(item.name(), item.path(), files);
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

    static AuthorCatalog parse(InputStream json) {
        try {
            return MAPPER.readValue(json, AuthorCatalog.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a valid manifest: " + e.getMessage(), e);
        }
    }

    private static AuthorCatalog readBundledCatalog() {
        try (var in = AuthorProfileLoader.class.getResourceAsStream(ROOT + "profile.json")) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Bundled author profile is invalid: " + e.getMessage(), e);
        }
    }

    private void validate() throws IOException {
        require(!catalog.items().isEmpty(), "the catalog has no items");
        for (var path : catalog.files()) {
            requireSafe(path);
            open(path).close();
        }
        var ids = new HashSet<String>();
        for (var item : catalog.items()) {
            require(item.id() != null && !item.id().isBlank() && ids.add(item.id()), "missing or duplicate item id: " + item.id());
            require(item.version() >= 1, item.id() + " version must be at least 1");
            require(item.area() != null && item.kind() != null, item.id() + " needs area and kind");
            require(notBlank(item.group()) && notBlank(item.title()) && notBlank(item.description()), item.id() + " needs group, title and description");
        }
        for (var item : catalog.items()) {
            item.requires().forEach(id -> require(ids.contains(id), item.id() + " requires unknown item " + id));
            validatePayload(item);
        }
    }

    private void validatePayload(AuthorItem item) {
        var id = item.id();
        switch (item.kind()) {
            case MARKETPLACE -> require(notBlank(item.name()) && notBlank(item.source()), id + " needs name and source");
            case PLUGIN, MCP_SERVER -> require(notBlank(item.name()), id + " needs name");
            case SKILL -> validateSkill(item);
            case TEAM -> {
                require(notBlank(item.name()), id + " needs name");
                requireListed(item.template());
            }
            case SETTINGS -> require(item.fragment() != null && item.fragment().isObject() && !item.fragment().isEmpty(),
                    id + " needs a non-empty fragment object");
        }
        if (item.kind() == AuthorKind.MCP_SERVER) {
            require(item.config() != null && item.config().isObject(), id + " needs a config object");
        }
    }

    private void validateSkill(AuthorItem item) {
        require(item.name() != null && KEBAB_CASE.matcher(item.name()).matches(), "skill name must be kebab-case: " + item.name());
        requireSafe(item.path());
        require(catalog.files().contains(item.path() + "/" + SKILL_FILE), "skill " + item.name() + " needs " + SKILL_FILE + " in files");
    }

    private Map<String, TeamTemplate> loadTeamTemplates() throws IOException {
        var templates = new LinkedHashMap<String, TeamTemplate>();
        for (var item : catalog.items()) {
            if (item.kind() == AuthorKind.TEAM) {
                try (var in = open(item.template())) {
                    var template = MAPPER.readValue(in, TeamTemplate.class);
                    TeamService.validate(template.name(), template.roles());
                    templates.put(item.id(), template);
                }
            }
        }
        return Map.copyOf(templates);
    }

    private void requireListed(String path) {
        requireSafe(path);
        require(catalog.files().contains(path), path + " is not listed in files");
    }

    private static void requireSafe(String path) {
        require(path != null && !path.isBlank() && !path.contains("..") && !path.startsWith("/") && !path.contains("\\"), "unsafe path: " + path);
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
