package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * UserMetadata — a value object that encapsulates user-defined metadata headers
 * ({@code x-amz-meta-*}).
 * <p>
 * Holds an immutable {@link Map} of header name → header value pairs.
 * </p>
 * <p>
 * Pure domain — NO framework dependencies.
 */
public record UserMetadata(Map<String, String> entries) {

    public UserMetadata {
        Objects.requireNonNull(entries, "entries must not be null");
        entries = Map.copyOf(entries);
    }

    /**
     * Creates a {@code UserMetadata} from the given map of user metadata entries.
     *
     * @param entries the user metadata entries (header name → value)
     * @return a new {@code UserMetadata}
     */
    public static UserMetadata of(Map<String, String> entries) {
        return new UserMetadata(entries);
    }

    /**
     * Returns the value for the given metadata key, or {@code null} if absent.
     *
     * @param key the metadata header name
     * @return the corresponding value, or {@code null}
     */
    public String get(String key) {
        return entries.get(key);
    }

    /**
     * Returns {@code true} if this container holds no metadata entries.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
