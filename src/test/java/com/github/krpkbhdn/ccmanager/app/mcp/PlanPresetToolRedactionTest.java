package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import com.github.krpkbhdn.ccmanager.preset.apply.ChangeAction;
import com.github.krpkbhdn.ccmanager.preset.apply.PlannedChange;
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PlanPresetToolRedactionTest {

    private static final String SOURCE = "https://user:s3cret-token@git.example.com/org/repo.git";

    @Test
    void outputShowsTheDetailButNeverThePreviewWithTheFullSource() {
        var presets = mock(ProjectPresetService.class);
        var apply = mock(PresetApplyService.class);
        var projects = mock(ProjectService.class);
        when(projects.find(1L)).thenReturn(Optional.of(new Project(1, "p", Path.of("."), null, null)));
        when(presets.get(2L)).thenReturn(Optional.of(new ProjectPreset(2, "x", "", null, false, false, 1, List.of())));
        when(apply.plan(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new PlannedChange(5, PresetComponentKind.MARKETPLACE, "m", ChangeAction.INSTALL, "Add the marketplace https://git.example.com/…", SOURCE)));

        var out = new PlanPresetTool(presets, apply, projects).call(JsonMapper.builder().build().createObjectNode().put("project_id", 1).put("preset_id", 2));

        assertThat(out).contains("git.example.com").doesNotContain("s3cret-token");
    }
}
