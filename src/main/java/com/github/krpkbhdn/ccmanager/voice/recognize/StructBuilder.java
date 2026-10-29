package com.github.krpkbhdn.ccmanager.voice.recognize;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.ArrayList;
import java.util.List;

/** Builds a C struct layout with the padding a C compiler inserts, which the Linker needs spelled out. */
final class StructBuilder {
    private final List<MemoryLayout> parts = new ArrayList<>();
    private long offset;
    private long align = 1;

    StructBuilder add(String name, MemoryLayout field) {
        pad(field.byteAlignment());
        parts.add(field.withName(name));
        offset += field.byteSize();
        align = Math.max(align, field.byteAlignment());
        return this;
    }

    StructBuilder add(String name, StructBuilder nested) {
        return add(name, nested.build());
    }

    StructLayout build() {
        pad(align);
        return MemoryLayout.structLayout(parts.toArray(MemoryLayout[]::new));
    }

    private void pad(long alignment) {
        long gap = (alignment - offset % alignment) % alignment;
        if (gap > 0) {
            parts.add(MemoryLayout.paddingLayout(gap));
            offset += gap;
        }
    }
}
