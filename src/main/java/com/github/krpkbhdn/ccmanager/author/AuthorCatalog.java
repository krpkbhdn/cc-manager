package com.github.krpkbhdn.ccmanager.author;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The bundled setup catalog: its items in display order and every file the profile ships. */
public record AuthorCatalog(List<AuthorItem> items, List<String> files) {

    public AuthorCatalog {
        items = items == null ? List.of() : List.copyOf(items);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public Optional<AuthorItem> byId(String id) {
        return items.stream().filter(item -> item.id().equals(id)).findFirst();
    }

    /** The ids plus everything they require, transitively, in catalog order. @throws IllegalArgumentException for an unknown id */
    public List<AuthorItem> requiresClosure(Collection<String> ids) {
        Set<String> wanted = new LinkedHashSet<>();
        var pending = new ArrayList<>(ids);
        while (!pending.isEmpty()) {
            var id = pending.removeLast();
            if (wanted.add(id)) {
                pending.addAll(byId(id).orElseThrow(() -> new IllegalArgumentException("unknown item " + id)).requires());
            }
        }
        return items.stream().filter(item -> wanted.contains(item.id())).toList();
    }
}
