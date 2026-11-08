package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Plans and applies the components of one kind. {@code apply} is called only for a planned CREATE, UPDATE or INSTALL and never overwrites. */
interface PresetComponentApplier {

    PresetComponentKind kind();

    /** Reads the project, writes nothing. May throw; the service reports that as a conflict. */
    PlannedChange plan(PresetComponent component, Path projectDir) throws IOException;

    ComponentOutcome apply(PresetComponent component, Path projectDir) throws IOException;

    /** Runs once after all components were applied, for work shared by this kind (e.g. one slow health check). Must not throw. */
    default List<ComponentOutcome> afterApply(List<ComponentOutcome> outcomes, Path projectDir) {
        return outcomes;
    }
}
