package com.github.krpkbhdn.ccmanager.extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Merges a settings fragment into a {@code settings.json}: objects merge recursively, arrays take the union, a missing value is added
 * and a different existing value is a conflict that is left as it is and reported by its dotted key.
 */
public final class SettingsFragmentMerger {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private SettingsFragmentMerger() {}

    /** {@code merged} is the existing settings plus every non-conflicting part of the fragment. */
    public record Result(JsonNode merged, List<String> conflicts, boolean changed) {

        public Result {
            conflicts = List.copyOf(conflicts);
        }
    }

    /** Pure merge; neither argument is modified. */
    public static Result merge(JsonNode existing, JsonNode fragment) {
        var conflicts = new ArrayList<String>();
        var merged = existing.deepCopy();
        var changed = mergeInto(merged, fragment, "", conflicts);
        return new Result(merged, conflicts, changed);
    }

    /**
     * Merges into the file, creating it when missing. The file is rewritten (temp file, then atomic move) only when something was added,
     * so an unreadable or invalid file is never touched.
     *
     * @throws ExtensionException when the existing file is not a JSON object or cannot be read or written
     */
    public static Result mergeFile(Path file, JsonNode fragment) {
        var existing = read(file);
        var result = merge(existing, fragment);
        if (result.changed()) {
            write(file, result.merged());
        }
        return result;
    }

    private static boolean mergeInto(JsonNode target, JsonNode fragment, String path, List<String> conflicts) {
        var changed = false;
        for (var entry : fragment.properties()) {
            var key = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            var wanted = entry.getValue();
            var object = (ObjectNode) target;
            var current = object.get(entry.getKey());
            if (current == null) {
                object.set(entry.getKey(), wanted.deepCopy());
                changed = true;
            } else if (current.isObject() && wanted.isObject()) {
                changed |= mergeInto(current, wanted, key, conflicts);
            } else if (current.isArray() && wanted.isArray()) {
                changed |= addMissing((ArrayNode) current, wanted);
            } else if (!current.equals(wanted)) {
                conflicts.add(key);
            }
        }
        return changed;
    }

    private static boolean addMissing(ArrayNode target, JsonNode wanted) {
        var changed = false;
        for (var element : wanted) {
            var present = false;
            for (var have : target) {
                present |= have.equals(element);
            }
            if (!present) {
                target.add(element.deepCopy());
                changed = true;
            }
        }
        return changed;
    }

    private static JsonNode read(Path file) {
        if (!Files.exists(file)) {
            return MAPPER.createObjectNode();
        }
        try {
            var text = Files.readString(file, StandardCharsets.UTF_8);
            var parsed = text.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(text);
            if (!parsed.isObject()) {
                throw new ExtensionException(file.getFileName() + " is not a JSON object");
            }
            return parsed;
        } catch (IOException e) {
            throw new ExtensionException("Could not read " + file.getFileName() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            if (e instanceof ExtensionException found) {
                throw found;
            }
            throw new ExtensionException(file.getFileName() + " is not valid JSON, nothing was changed");
        }
    }

    private static void write(Path link, JsonNode content) {
        Path temp = null;
        try {
            var file = Files.exists(link) ? link.toRealPath() : link.toAbsolutePath();
            var directory = file.getParent();
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
            Files.writeString(temp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(content) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new ExtensionException("Could not write " + link.getFileName() + ": " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path temp) {
        try {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ignored) {
            // a leftover temp file is harmless
        }
    }
}
