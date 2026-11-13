package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class McpServerApplier implements PresetComponentApplier {

    private static final Logger log = LoggerFactory.getLogger(McpServerApplier.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final McpService mcp;

    McpServerApplier(McpService mcp) {
        this.mcp = mcp;
    }

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.MCP_SERVER;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) {
        var known = mcp.servers(projectDir).stream().map(McpServerConfig::name).anyMatch(component.name()::equals);
        if (known) {
            return PlannedChange.of(component, ChangeAction.PRESENT, "MCP server already configured");
        }
        var config = config(component);
        var stdio = !config.isObject() || config.hasNonNull("command");
        var detail = stdio ? CodeExecutingPaths.runsCode("MCP server " + component.name() + " (starts a local command)")
                : "Written to .mcp.json in the project, which is usually committed: check it contains no secrets. Server: " + component.name();
        return PlannedChange.of(component, stdio ? ChangeAction.NEEDS_CONFIRMATION : ChangeAction.INSTALL, detail).withPreview(describe(config));
    }

    private static JsonNode config(PresetComponent component) {
        var config = component.payload().path("config");
        if (!config.isString()) {
            return config;
        }
        try {
            return JSON.readTree(config.asString());
        } catch (JacksonException e) {
            return config;
        }
    }

    /** Shows command, args, url and the KEYS of env and headers; their values are never shown. */
    private static String describe(JsonNode config) {
        if (!config.isObject()) {
            return "Config is not a JSON object, it is not shown";
        }
        var text = new StringBuilder();
        for (var field : List.of("type", "command", "args", "url")) {
            if (config.hasNonNull(field)) {
                var value = config.path(field);
                var shown = field.equals("url") ? McpTargetRedaction.redact(value.asString()) : value.isArray() ? String.join(" ", value.valueStream().map(JsonNode::asString).toList()) : value.asString();
                text.append(field).append(": ").append(shown).append('\n');
            }
        }
        for (var field : List.of("env", "headers")) {
            if (config.path(field).isObject()) {
                text.append(field).append(" keys: ").append(String.join(", ", config.path(field).propertyNames())).append(" (values hidden)\n");
            }
        }
        return text.toString();
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) {
        var config = component.payload().path("config");
        mcp.add(component.name(), config.isString() ? config.asString() : config.toString(), ExtensionScope.PROJECT, projectDir);
        return ComponentOutcome.of(component, OutcomeStatus.APPLIED, "MCP server added");
    }

    /** One health check for all added servers; it checks every server of the project, so it is slow and must never fail an already written component. */
    @Override
    public List<ComponentOutcome> afterApply(List<ComponentOutcome> outcomes, Path projectDir) {
        var added = outcomes.stream().filter(outcome -> outcome.kind() == kind() && outcome.status() == OutcomeStatus.APPLIED).toList();
        if (added.isEmpty()) {
            return outcomes;
        }
        Set<String> needSignIn;
        try {
            needSignIn = mcp.health(projectDir).stream().filter(health -> health.status() == McpStatus.NEEDS_AUTHENTICATION)
                    .map(McpHealth::name).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("MCP health check failed after adding servers: {}", e.getClass().getSimpleName());
            return outcomes;
        }
        return outcomes.stream().map(outcome -> added.contains(outcome) && needSignIn.contains(outcome.title())
                ? new ComponentOutcome(outcome.componentId(), outcome.kind(), outcome.title(), OutcomeStatus.SIGN_IN_NEEDED, "MCP server added, sign in on the MCP page")
                : outcome).toList();
    }
}
