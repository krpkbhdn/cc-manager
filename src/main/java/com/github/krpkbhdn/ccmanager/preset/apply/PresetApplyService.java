package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies a preset to a project folder without a Claude session. The CLI writes enabledPlugins into settings.json while installing
 * plugins, so settings are merged last; one failing component never stops the others.
 */
@Service
public class PresetApplyService {

    private static final Logger log = LoggerFactory.getLogger(PresetApplyService.class);
    private static final Comparator<PresetComponentKind> APPLY_ORDER = Comparator.comparingInt(kind -> switch (kind) {
        case MARKETPLACE -> 0;
        case PLUGIN -> 1;
        case MCP_SERVER -> 2;
        case SKILL, AGENT, FILE -> 3;
        case INSTRUCTION -> 4;
        case SETTINGS -> 5;
    });

    private final ProjectPresetService presets;
    private final PresetApplicationService applications;
    private final Map<PresetComponentKind, PresetComponentApplier> appliers = new EnumMap<>(PresetComponentKind.class);
    private final Map<Path, ReentrantLock> locks = new ConcurrentHashMap<>();

    PresetApplyService(ProjectPresetService presets, PresetApplicationService applications, List<PresetComponentApplier> appliers) {
        this.presets = presets;
        this.applications = applications;
        appliers.forEach(applier -> this.appliers.put(applier.kind(), applier));
    }

    /** @return one line per component in preset order; nothing is written */
    public List<PlannedChange> plan(long presetId, Path projectDir) {
        return locked(projectDir, () -> presets.get(presetId).orElseThrow(() -> new IllegalArgumentException("Unknown preset " + presetId))
                .components().stream().map(component -> plan(component, projectDir)).toList());
    }

    private <T> T locked(Path projectDir, Supplier<T> action) {
        var lock = locks.computeIfAbsent(projectDir.toAbsolutePath().normalize(), key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** Plans again, since the project may have changed after the preview, then applies the selected components that can be applied. */
    public PresetApplyResult apply(long projectId, long presetId, Path projectDir, Set<Long> selectedComponentIds) {
        return locked(projectDir, () -> applyLocked(projectId, presetId, projectDir, selectedComponentIds));
    }

    private PresetApplyResult applyLocked(long projectId, long presetId, Path projectDir, Set<Long> selectedComponentIds) {
        var preset = presets.get(presetId).orElseThrow(() -> new IllegalArgumentException("Unknown preset " + presetId));
        var ordered = preset.components().stream().sorted(Comparator.comparing(PresetComponent::kind, APPLY_ORDER)).toList();
        var outcomes = new ArrayList<ComponentOutcome>();
        var applied = 0;
        var allPresent = true;
        for (var component : ordered) {
            var change = plan(component, projectDir);
            allPresent &= change.action() == ChangeAction.PRESENT;
            var outcome = !selectedComponentIds.contains(component.id()) ? ComponentOutcome.of(component, OutcomeStatus.SKIPPED, "Not selected")
                    : change.action().writes() ? applyOne(component, projectDir)
                    : ComponentOutcome.of(component, OutcomeStatus.SKIPPED, change.detail());
            applied += outcome.status() == OutcomeStatus.APPLIED || outcome.status() == OutcomeStatus.SIGN_IN_NEEDED ? 1 : 0;
            outcomes.add(outcome);
        }
        for (var applier : appliers.values()) {
            outcomes = new ArrayList<>(applier.afterApply(outcomes, projectDir));
        }
        var failed = outcomes.stream().anyMatch(outcome -> outcome.status() == OutcomeStatus.FAILED);
        var recorded = !failed && (applied > 0 || allPresent);
        if (recorded) {
            applications.markApplied(projectId, presetId, preset.revision());
        }
        return new PresetApplyResult(outcomes, recorded);
    }

    private PlannedChange plan(PresetComponent component, Path projectDir) {
        try {
            return appliers.get(component.kind()).plan(component, projectDir);
        } catch (Exception e) {
            log.warn("Could not plan {} component {}: {}", component.kind(), component.id(), e.getClass().getSimpleName());
            return PlannedChange.of(component, ChangeAction.CONFLICT, "Could not check: " + ErrorText.redacted(e));
        }
    }

    private ComponentOutcome applyOne(PresetComponent component, Path projectDir) {
        try {
            return appliers.get(component.kind()).apply(component, projectDir);
        } catch (Exception e) {
            log.warn("Could not apply {} component {}: {}", component.kind(), component.id(), e.getClass().getSimpleName());
            return ComponentOutcome.of(component, OutcomeStatus.FAILED, ErrorText.redacted(e));
        }
    }
}
