package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
class AgentApplier implements PresetComponentApplier {

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.AGENT;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) throws IOException {
        var file = file(component, projectDir);
        var content = content(component);
        var path = ".claude/agents/" + component.name() + ".md";
        return switch (ProjectFiles.compare(file, content)) {
            case MISSING -> CodeExecutingPaths.agentDeclaresHooks(content)
                    ? PlannedChange.of(component, ChangeAction.NEEDS_CONFIRMATION, CodeExecutingPaths.runsCode(path + " (declares hooks)")).withPreview(content)
                    : PlannedChange.of(component, ChangeAction.CREATE, "Create " + path).withPreview(content);
            case IDENTICAL -> PlannedChange.of(component, ChangeAction.PRESENT, "Agent already defined");
            case DIFFERENT -> PlannedChange.of(component, ChangeAction.CONFLICT, "An agent file with this name exists and differs, it is left unchanged");
        };
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) throws IOException {
        ProjectFiles.createNew(file(component, projectDir), content(component));
        return ComponentOutcome.of(component, OutcomeStatus.APPLIED, "Agent created");
    }

    private static Path file(PresetComponent component, Path projectDir) {
        return ProjectFiles.resolve(projectDir, ".claude/agents/" + component.name() + ".md");
    }

    private static String content(PresetComponent component) {
        return component.payload().path("content").asString("");
    }
}
