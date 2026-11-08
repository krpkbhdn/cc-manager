package com.github.krpkbhdn.ccmanager.usage.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses the markdown that {@code claude /context} prints; output without the "Context Usage" heading is not a breakdown. */
final class ContextBreakdownParser {

    private static final String HEADING = "## Context Usage";
    private static final String SECTION_PREFIX = "### ";
    private static final String CATEGORIES = "Estimated usage by category";
    private static final String MCP_TOOLS = "MCP Tools";
    private static final String AGENTS = "Custom Agents";
    private static final String MEMORY_FILES = "Memory Files";
    private static final String SKILLS = "Skills";
    private static final Set<String> KNOWN_SECTIONS = Set.of(CATEGORIES, MCP_TOOLS, AGENTS, MEMORY_FILES, SKILLS);
    private static final Pattern MODEL = Pattern.compile("\\*\\*Model:\\*\\*\\s*(\\S+)");
    private static final Pattern TOKENS = Pattern.compile("\\*\\*Tokens:\\*\\*\\s*(\\S+)\\s*/\\s*(\\S+)");
    private static final Pattern AMOUNT = Pattern.compile("(~|<\\s*)?(\\d+(?:\\.\\d+)?)\\s*([kKmM])?");
    private static final long THOUSAND = 1_000;
    private static final long MILLION = 1_000_000;

    private ContextBreakdownParser() {
    }

    static Optional<ContextBreakdown> parse(String markdown, Instant at) {
        if (markdown == null || !markdown.contains(HEADING)) {
            return Optional.empty();
        }
        var tokens = TOKENS.matcher(markdown);
        var model = MODEL.matcher(markdown);
        if (!tokens.find()) {
            return Optional.empty();
        }
        var used = amount(tokens.group(1));
        var window = amount(tokens.group(2));
        if (used == null || window == null) {
            return Optional.empty();
        }
        var sections = sections(markdown);
        return Optional.of(new ContextBreakdown(model.find() ? model.group(1) : "", used.tokens(), window.tokens(),
                categories(sections.getOrDefault(CATEGORIES, List.of())),
                items(sections.getOrDefault(MEMORY_FILES, List.of()), 1, 0),
                items(sections.getOrDefault(MCP_TOOLS, List.of()), 0, 1),
                items(sections.getOrDefault(AGENTS, List.of()), 0, 1),
                items(sections.getOrDefault(SKILLS, List.of()), 0, 1),
                false, at, otherSections(sections), markdown));
    }

    private static List<ContextBreakdown.Section> otherSections(Map<String, List<List<String>>> sections) {
        var others = new ArrayList<ContextBreakdown.Section>();
        sections.forEach((title, rows) -> {
            if (!KNOWN_SECTIONS.contains(title)) {
                var items = genericItems(rows);
                if (!items.isEmpty()) {
                    others.add(new ContextBreakdown.Section(title, items));
                }
            }
        });
        return others;
    }

    private static List<ContextBreakdown.Item> genericItems(List<List<String>> rows) {
        var items = new ArrayList<ContextBreakdown.Item>();
        for (var row : rows) {
            var tokens = row.size() >= 2 ? amount(row.get(row.size() - 1)) : null;
            if (tokens != null) {
                items.add(new ContextBreakdown.Item(row.get(0), row.size() >= 3 ? row.get(1) : "", tokens.tokens(), tokens.approximate()));
            }
        }
        return items;
    }

    private static Map<String, List<List<String>>> sections(String markdown) {
        var sections = new LinkedHashMap<String, List<List<String>>>();
        List<List<String>> current = null;
        for (var line : markdown.split("\\R")) {
            if (line.startsWith(SECTION_PREFIX)) {
                current = new ArrayList<>();
                sections.put(line.substring(SECTION_PREFIX.length()).strip(), current);
            } else if (current != null && line.strip().startsWith("|")) {
                current.add(Arrays.stream(line.strip().replaceAll("\\|$", "").split("\\|", -1)).skip(1).map(String::strip).toList());
            }
        }
        return sections;
    }

    private static List<ContextBreakdown.Category> categories(List<List<String>> rows) {
        var categories = new ArrayList<ContextBreakdown.Category>();
        for (var row : rows) {
            var tokens = row.size() >= 3 ? amount(row.get(1)) : null;
            if (tokens != null) {
                categories.add(new ContextBreakdown.Category(row.get(0), tokens.tokens(), percent(row.get(2))));
            }
        }
        return categories;
    }

    private static List<ContextBreakdown.Item> items(List<List<String>> rows, int nameColumn, int groupColumn) {
        var items = new ArrayList<ContextBreakdown.Item>();
        for (var row : rows) {
            var tokens = row.size() >= 3 ? amount(row.get(2)) : null;
            if (tokens != null) {
                items.add(new ContextBreakdown.Item(row.get(nameColumn), row.get(groupColumn), tokens.tokens(), tokens.approximate()));
            }
        }
        return items;
    }

    private static double percent(String cell) {
        try {
            return Double.parseDouble(cell.replace("%", "").strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record Amount(long tokens, boolean approximate) {
    }

    /** Reads {@code 599}, {@code 2.3k}, {@code 1m}, {@code ~90} and {@code < 20}; null for anything else (header cells, separators). */
    private static Amount amount(String cell) {
        var matcher = AMOUNT.matcher(cell.strip());
        if (!matcher.matches()) {
            return null;
        }
        var multiplier = matcher.group(3) == null ? 1 : "k".equalsIgnoreCase(matcher.group(3)) ? THOUSAND : MILLION;
        return new Amount(Math.round(Double.parseDouble(matcher.group(2)) * multiplier), matcher.group(1) != null);
    }
}
