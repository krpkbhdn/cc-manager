package com.github.krpkbhdn.ccmanager.preset.apply;

import java.util.List;

/**
 * @param outcomes in the order the components were applied
 * @param recorded whether the preset revision was recorded as applied to the project
 */
public record PresetApplyResult(List<ComponentOutcome> outcomes, boolean recorded) {

    public PresetApplyResult {
        outcomes = List.copyOf(outcomes);
    }
}
