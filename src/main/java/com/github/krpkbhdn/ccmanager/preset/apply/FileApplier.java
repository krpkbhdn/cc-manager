package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** A project file with fixed content plus optional .gitignore lines; an existing differing file is never overwritten. */
@Component
class FileApplier implements PresetComponentApplier {

    private static final String GITIGNORE = ".gitignore";

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.FILE;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) throws IOException {
        var path = component.payload().path("path").asString("");
        var resolved = ProjectFiles.resolve(projectDir, path);
        var state = ProjectFiles.compare(resolved, content(component));
        if (CodeExecutingPaths.matches(projectDir, resolved)) {
            return PlannedChange.of(component, ChangeAction.NEEDS_CONFIRMATION, path + " runs code automatically, create it yourself after reviewing it")
                    .withPreview(content(component));
        }
        var missingLines = missingIgnoreLines(component, projectDir);
        return switch (state) {
            case DIFFERENT -> PlannedChange.of(component, ChangeAction.CONFLICT, path + " exists and differs, it is left unchanged");
            case MISSING -> PlannedChange.of(component, ChangeAction.CREATE, "Create " + path + ignoreNote(missingLines)).withPreview(content(component));
            case IDENTICAL -> missingLines.isEmpty() ? PlannedChange.of(component, ChangeAction.PRESENT, path + " already exists")
                    : PlannedChange.of(component, ChangeAction.UPDATE, path + " exists" + ignoreNote(missingLines));
        };
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) throws IOException {
        var relative = component.payload().path("path").asString("");
        var file = ProjectFiles.resolve(projectDir, relative);
        if (CodeExecutingPaths.matches(projectDir, file)) {
            throw new IllegalArgumentException(relative + " runs code automatically and needs confirmation");
        }
        var missingLines = missingIgnoreLines(component, projectDir);
        if (ProjectFiles.compare(file, content(component)) == ProjectFiles.State.MISSING) {
            ProjectFiles.createNew(file, content(component));
        }
        if (!missingLines.isEmpty()) {
            var gitignore = ProjectFiles.resolve(projectDir, GITIGNORE);
            var existing = ProjectFiles.read(gitignore);
            var eol = ProjectFiles.lineEnding(existing);
            var text = new StringBuilder(existing);
            if (!existing.isEmpty() && !existing.endsWith("\n")) {
                text.append(eol);
            }
            missingLines.forEach(line -> text.append(line).append(eol));
            ProjectFiles.replaceIfUnchanged(gitignore, existing, text.toString());
        }
        return ComponentOutcome.of(component, OutcomeStatus.APPLIED, "File written");
    }

    private static List<String> missingIgnoreLines(PresetComponent component, Path projectDir) throws IOException {
        var have = ProjectFiles.read(ProjectFiles.resolve(projectDir, GITIGNORE)).lines().map(String::strip).toList();
        var missing = new ArrayList<String>();
        for (var line : component.payload().path("gitignore")) {
            var wanted = line.asString("").strip();
            if (!wanted.isEmpty() && !have.contains(wanted) && !missing.contains(wanted)) {
                missing.add(wanted);
            }
        }
        return missing;
    }

    private static String ignoreNote(List<String> lines) {
        return lines.isEmpty() ? "" : ", add " + lines.size() + " line(s) to " + GITIGNORE;
    }

    private static String content(PresetComponent component) {
        return component.payload().path("content").asString("");
    }
}
