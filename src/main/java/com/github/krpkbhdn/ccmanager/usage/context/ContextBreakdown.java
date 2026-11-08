package com.github.krpkbhdn.ccmanager.usage.context;

import java.time.Instant;
import java.util.List;

/**
 * What fills a session's context window, as reported by {@code claude /context}; item lists are empty when claude omitted the section.
 * {@code otherSections} holds every table the parser has no dedicated list for, {@code raw} the full markdown as printed.
 */
public record ContextBreakdown(String model, long usedTokens, long windowTokens, List<Category> categories, List<Item> memoryFiles,
        List<Item> mcpTools, List<Item> agents, List<Item> skills, boolean approximateMessages, Instant measuredAt,
        List<Section> otherSections, String raw) {

    public ContextBreakdown {
        categories = List.copyOf(categories);
        memoryFiles = List.copyOf(memoryFiles);
        mcpTools = List.copyOf(mcpTools);
        agents = List.copyOf(agents);
        skills = List.copyOf(skills);
        otherSections = List.copyOf(otherSections);
    }

    public ContextBreakdown withApproximateMessages(boolean approximate) {
        return new ContextBreakdown(model, usedTokens, windowTokens, categories, memoryFiles, mcpTools, agents, skills, approximate, measuredAt,
                otherSections, raw);
    }

    /** One row of "Estimated usage by category", the name as claude prints it. */
    public record Category(String name, long tokens, double percent) {
    }

    /** A memory file (group = type, name = path), an MCP tool (group = server), an agent or skill (group = source). */
    public record Item(String name, String group, long tokens, boolean approximate) {
    }

    /** A table without a dedicated list, titled as claude prints it; item group is empty when the table has only two columns. */
    public record Section(String title, List<Item> items) {

        public Section {
            items = List.copyOf(items);
        }
    }
}
