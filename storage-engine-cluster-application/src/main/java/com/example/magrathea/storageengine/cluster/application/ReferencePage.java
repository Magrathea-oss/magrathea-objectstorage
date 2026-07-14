package com.example.magrathea.storageengine.cluster.application;

import java.util.List;

/** Bounded current-reference page returned by the consensus read-only path. */
public record ReferencePage(
        List<ObjectReferenceGeneration> references,
        ReferencePageQuery.Cursor nextExclusiveCursor,
        boolean terminal) {
    public ReferencePage {
        references = List.copyOf(references);
        if (references.size() > ReferencePageQuery.MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("reference page exceeds the hard limit");
        }
        if (references.isEmpty() && !terminal) {
            throw new IllegalArgumentException("an empty reference page must be terminal");
        }
        if (!references.isEmpty()) {
            ReferencePageQuery.Cursor expected = ReferencePageQuery.Cursor.after(
                    references.get(references.size() - 1));
            if (!expected.equals(nextExclusiveCursor)) {
                throw new IllegalArgumentException("page cursor must identify its last reference");
            }
        } else if (nextExclusiveCursor != null) {
            throw new IllegalArgumentException("an empty page cannot expose a next cursor");
        }
    }
}
