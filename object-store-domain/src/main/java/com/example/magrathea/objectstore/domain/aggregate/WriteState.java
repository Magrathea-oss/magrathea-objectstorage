package com.example.magrathea.objectstore.domain.aggregate;

/**
 * Write lifecycle state for an S3 object.
 * <p>
 * States:
 * <ul>
 *   <li>{@code CREATED} — object metadata created, no content yet</li>
 *   <li>{@code WRITING} — content is being uploaded</li>
 *   <li>{@code WRITTEN} — content fully uploaded</li>
 *   <li>{@code DELETED} — terminal state, object has been deleted</li>
 * </ul>
 * </p>
 * Transitions:
 * <ul>
 *   <li>{@code CREATED → WRITING} — initiate upload</li>
 *   <li>{@code WRITING → WRITTEN} — complete upload</li>
 *   <li>{@code WRITTEN → DELETED} — delete object</li>
 * </ul>
 * </p>
 * Pure domain — NO framework dependencies.
 */
public enum WriteState {
    CREATED,
    WRITING,
    WRITTEN,
    DELETED
}
