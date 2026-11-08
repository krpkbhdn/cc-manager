package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Writes an instruction as a managed block in the project's CLAUDE.md. */
@Component
class InstructionApplier implements PresetComponentApplier {

    private static final String CLAUDE_MD = "CLAUDE.md";

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.INSTRUCTION;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) throws IOException {
        var plan = sectionPlan(component, projectDir);
        var change = PlannedChange.of(component, plan.action(), plan.detail());
        return plan.text() == null ? change : change.withPreview(ClaudeMdSections.block(component.name(), markdown(component)));
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) throws IOException {
        var plan = sectionPlan(component, projectDir);
        if (plan.text() != null) {
            ProjectFiles.replace(ProjectFiles.resolve(projectDir, CLAUDE_MD), plan.text());
        } else if (plan.action() == ChangeAction.CONFLICT) {
            return ComponentOutcome.of(component, OutcomeStatus.SKIPPED, plan.detail());
        }
        return ComponentOutcome.of(component, OutcomeStatus.APPLIED, plan.detail());
    }

    private static ClaudeMdSections.Plan sectionPlan(PresetComponent component, Path projectDir) throws IOException {
        var file = ProjectFiles.resolve(projectDir, CLAUDE_MD);
        return ClaudeMdSections.plan(ProjectFiles.read(file), component.name(), markdown(component));
    }

    private static String markdown(PresetComponent component) {
        return component.payload().path("markdown").asString("");
    }
}
