package com.github.krpkbhdn.ccmanager.extension.skill;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Skills are plain directories with a SKILL.md: ~/.claude/skills (user), PROJECT/.claude/skills (project) and the
 * skills folder of each enabled plugin (read-only). There is no CLI for them, so this service works on the files.
 */
@Service
public class SkillService {

    static final String SKILL_FILE = "SKILL.md";
    private static final String CLAUDE_DIRECTORY = ".claude";
    private static final String SKILLS_DIRECTORY = "skills";
    private static final int MAX_NAME_LENGTH = 64;
    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final HostEnvironment host;
    private final PluginService plugins;

    public SkillService(HostEnvironment host, PluginService plugins) {
        this.host = host;
        this.plugins = plugins;
    }

    /** User skills, plus the project's own and those of plugins active for it when a project directory is given. */
    public List<Skill> list(Path projectDirectory) {
        var skills = new ArrayList<>(readDirectory(skillsRoot(ExtensionScope.USER, null), ExtensionScope.USER, ""));
        if (projectDirectory != null) {
            skills.addAll(readDirectory(skillsRoot(ExtensionScope.PROJECT, projectDirectory), ExtensionScope.PROJECT, ""));
        }
        activePlugins(projectDirectory).forEach(plugin ->
                skills.addAll(readDirectory(plugin.installPath().resolve(SKILLS_DIRECTORY), plugin.scope(), plugin.id())));
        return List.copyOf(skills);
    }

    public String read(Skill skill) {
        try {
            return Files.readString(skill.file());
        } catch (IOException e) {
            throw new ExtensionException("Cannot read " + skill.file() + ": " + e.getMessage());
        }
    }

    /** @param scope USER or PROJECT; LOCAL has no skills folder */
    public Skill create(ExtensionScope scope, Path projectDirectory, String name, String description) {
        description = description.replaceAll("\\s+", " ").strip();
        requireValid(name, description);
        var directory = skillsRoot(scope, projectDirectory).resolve(name);
        if (Files.exists(directory)) {
            throw new ExtensionException("A skill named " + name + " already exists");
        }
        var file = directory.resolve(SKILL_FILE);
        try {
            Files.createDirectories(directory);
            Files.writeString(file, SkillFrontmatter.render(name, description));
        } catch (IOException e) {
            throw new ExtensionException("Cannot create the skill: " + e.getMessage());
        }
        return new Skill(name, description, scope, "", file);
    }

    /** Deletes the skill's whole directory; only user and project skills, never a plugin's. */
    public void delete(Skill skill) {
        var directory = skill.directory();
        var inSkillsFolder = directory.getParent() != null && directory.getParent().getFileName().toString().equals(SKILLS_DIRECTORY)
                && directory.getParent().getParent() != null
                && (directory.getParent().getParent().getFileName().toString().equals(CLAUDE_DIRECTORY)
                || directory.getParent().equals(skillsRoot(ExtensionScope.USER, null)));
        if (skill.readOnly() || !inSkillsFolder || !Files.isRegularFile(skill.file())) {
            throw new ExtensionException("Only a user or project skill can be deleted");
        }
        try {
            if (!isDirectChildOf(directory.getParent(), directory)) {
                throw new ExtensionException("Refusing to delete " + directory + ": it is a link to somewhere else");
            }
        } catch (IOException e) {
            throw new ExtensionException("Cannot delete " + directory + ": " + e.getMessage());
        }
        try {
            TreeDeleter.delete(directory);
        } catch (IOException e) {
            throw new ExtensionException("Cannot delete " + directory + ": " + e.getMessage());
        }
    }

    /** Where a skill of this name lives in the scope, whether or not it exists yet. */
    public Path skillDirectory(ExtensionScope scope, Path projectDirectory, String name) {
        return skillsRoot(scope, projectDirectory).resolve(name);
    }

    private Path skillsRoot(ExtensionScope scope, Path projectDirectory) {
        return switch (scope) {
            case USER -> host.claudeConfigDirectory().resolve(SKILLS_DIRECTORY);
            case PROJECT -> ExtensionScope.PROJECT.workingDirectory(projectDirectory).resolve(CLAUDE_DIRECTORY).resolve(SKILLS_DIRECTORY);
            case LOCAL -> throw new ExtensionException("Skills exist only in user and project scope");
        };
    }

    private List<InstalledPlugin> activePlugins(Path projectDirectory) {
        return plugins.installed().stream()
                .filter(InstalledPlugin::enabled)
                .filter(plugin -> plugin.scope() == ExtensionScope.USER
                        || projectDirectory != null && projectDirectory.equals(plugin.projectPath()))
                .toList();
    }

    private static List<Skill> readDirectory(Path root, ExtensionScope scope, String pluginId) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .sorted()
                    .map(directory -> readSkill(root, directory, scope, pluginId))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            throw new ExtensionException("Cannot read " + root + ": " + e.getMessage());
        }
    }

    private static Optional<Skill> readSkill(Path root, Path directory, ExtensionScope scope, String pluginId) {
        var file = directory.resolve(SKILL_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            if (!isDirectChildOf(root, directory)) {
                return Optional.empty();
            }
            var fields = SkillFrontmatter.parse(Files.readString(file));
            var name = fields.getOrDefault("name", "");
            return Optional.of(new Skill(name.isEmpty() ? directory.getFileName().toString() : name,
                    fields.getOrDefault("description", ""), scope, pluginId, file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** False for a symlink or junction whose target lies outside the skills root. */
    private static boolean isDirectChildOf(Path root, Path directory) throws IOException {
        return directory.toRealPath().getParent().equals(root.toRealPath());
    }

    private static void requireValid(String name, String description) {
        if (name.length() > MAX_NAME_LENGTH || !KEBAB_CASE.matcher(name).matches()) {
            throw new ExtensionException("The name must be kebab-case (lowercase letters, digits, '-'), up to " + MAX_NAME_LENGTH + " characters");
        }
        if (description.isBlank()) {
            throw new ExtensionException("The description must not be empty");
        }
    }
}
