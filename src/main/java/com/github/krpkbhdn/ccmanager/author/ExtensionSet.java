package com.github.krpkbhdn.ccmanager.author;

import java.util.List;

/** Marketplaces, plugins ({@code name@marketplace}), skills and MCP servers installed before Claude takes over. */
public record ExtensionSet(List<ProfileMarketplace> marketplaces, List<String> plugins, List<ProfileSkill> skills,
        List<ProfileMcpServer> mcpServers) {

    public ExtensionSet {
        marketplaces = marketplaces == null ? List.of() : List.copyOf(marketplaces);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        skills = skills == null ? List.of() : List.copyOf(skills);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
    }
}
