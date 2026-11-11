package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** A skill is a folder under .claude/skills; it is created whole or not at all, and an existing one is never changed. */
@Component
class SkillApplier implements PresetComponentApplier {

    private static final String SKILLS = ".claude/skills/";

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.SKILL;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) throws IOException {
        var target = ProjectFiles.resolve(projectDir, SKILLS + component.name());
        var files = files(component, projectDir);
        if (!Files.exists(target)) {
            var preview = new StringBuilder();
            files.forEach((path, text) -> preview.append("--- ").append(path).append('\n').append(text).append('\n'));
            return PlannedChange.of(component, ChangeAction.CREATE, "Create " + SKILLS + component.name() + " (" + files.size() + " file(s))")
                    .withPreview(preview.toString());
        }
        for (var file : files.entrySet()) {
            if (ProjectFiles.compare(target.resolve(file.getKey()), file.getValue()) != ProjectFiles.State.IDENTICAL) {
                return PlannedChange.of(component, ChangeAction.CONFLICT, "The skill folder exists and differs, it is left unchanged");
            }
        }
        return PlannedChange.of(component, ChangeAction.PRESENT, "Skill already installed");
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) throws IOException {
        var target = ProjectFiles.resolve(projectDir, SKILLS + component.name());
        var files = files(component, projectDir);
        var parent = target.getParent();
        Files.createDirectories(parent);
        var staging = Files.createTempDirectory(parent, "." + component.name() + "-");
        try {
            for (var file : files.entrySet()) {
                var destination = staging.resolve(file.getKey()).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IllegalArgumentException("A skill file leaves the skill folder: " + file.getKey());
                }
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, file.getValue());
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (Files.exists(staging)) {
                TreeDeleter.delete(staging);
            }
        }
        return ComponentOutcome.of(component, OutcomeStatus.APPLIED, "Skill created");
    }

    private static Map<String, String> files(PresetComponent component, Path projectDir) {
        var files = new LinkedHashMap<String, String>();
        for (var entry : component.payload().path("files").properties()) {
            var key = entry.getKey().strip().replace('\\', '/');
            ProjectFiles.resolve(projectDir, SKILLS + component.name() + "/" + key);
            files.put(key, entry.getValue().asString(""));
        }
        return files;
    }
}
