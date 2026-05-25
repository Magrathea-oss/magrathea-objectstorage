package com.example.magrathea.objectstorage.domain.aggregate;

import com.example.magrathea.objectstorage.domain.event.ObjectStorageEvent;
import com.example.magrathea.objectstorage.domain.valueobject.Region;
import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 Bucket — aggregate root as a Java 17+ record.
 * Immutable, transparent, zero boilerplate.
 * A bucket is a container for objects stored in Amazon S3.
 *
 * Pure domain — NO framework dependencies.
 *
 * Transitions return NEW instances with accumulated domain events.
 * Call {@link #domainEvents()} to retrieve events after a transition.
 * Call {@link #clearEvents()} to obtain a clean instance for persistence.
 */
public record Bucket(
    Id id,
    String name,
    Region region,
    StorageClass storageClass,
    boolean versioningEnabled,
    boolean encryptionEnabled,
    Configuration configuration,
    List<ObjectStorageEvent> events
) {

    public record Id(String value) {
        public Id { Objects.requireNonNull(value); }
        public static Id generate() { return new Id(UUID.randomUUID().toString()); }
        public static Id of(String value) { return new Id(value); }
    }

    /**
     * Compact constructor with validation.
     */
    public Bucket {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(region);
        Objects.requireNonNull(storageClass);
        Objects.requireNonNull(events);
        if (name.isBlank()) throw new IllegalArgumentException("Bucket name must not be blank");
        if (name.length() < 3 || name.length() > 63)
            throw new IllegalArgumentException("Bucket name must be 3-63 characters");
        if (!name.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$"))
            throw new IllegalArgumentException("Bucket name must be lowercase, no underscores");
    }

    /**
     * Factory method — create a new bucket.
     * Records a {@link ObjectStorageEvent.BucketCreated} event.
     */
    public static Bucket create(Id id, String name, Region region, StorageClass storageClass) {
        return create(id, name, region, storageClass, null);
    }

    /**
     * Factory method — create a new bucket with an optional initial configuration.
     * Records a {@link ObjectStorageEvent.BucketCreated} event.
     */
    public static Bucket create(Id id, String name, Region region, StorageClass storageClass,
                                 Configuration configuration) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(region);
        Objects.requireNonNull(storageClass);
        var now = Instant.now();
        var events = List.<ObjectStorageEvent>of(
            new ObjectStorageEvent.BucketCreated(id, name, now)
        );
        return new Bucket(id, name, region, storageClass, false, false, configuration, events);
    }

    /**
     * Factory method — restore from persistence (no events).
     */
    public static Bucket restore(Id id, String name, Region region, StorageClass storageClass,
                                  boolean versioningEnabled, boolean encryptionEnabled,
                                  Configuration configuration) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(region);
        Objects.requireNonNull(storageClass);
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            configuration, List.of());
    }

    /**
     * Legacy restore without configuration — defaults to null.
     */
    public static Bucket restore(Id id, String name, Region region, StorageClass storageClass,
                                  boolean versioningEnabled, boolean encryptionEnabled) {
        return restore(id, name, region, storageClass, versioningEnabled, encryptionEnabled, null);
    }

    /**
     * Enable versioning. Returns new instance with a {@link ObjectStorageEvent.BucketVersioningEnabled} event.
     */
    public Bucket withVersioningEnabled() {
        var newEvents = appendEvent(
            new ObjectStorageEvent.BucketVersioningEnabled(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, true, encryptionEnabled, configuration, newEvents);
    }

    /**
     * Suspend versioning. Returns new instance with a {@link ObjectStorageEvent.BucketVersioningSuspended} event.
     */
    public Bucket withVersioningSuspended() {
        var newEvents = appendEvent(
            new ObjectStorageEvent.BucketVersioningSuspended(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, false, encryptionEnabled, configuration, newEvents);
    }

    /**
     * Enable encryption. Returns new instance with a {@link ObjectStorageEvent.BucketEncryptionEnabled} event.
     */
    public Bucket withEncryptionEnabled() {
        var newEvents = appendEvent(
            new ObjectStorageEvent.BucketEncryptionEnabled(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, true, configuration, newEvents);
    }

    /**
     * Set the bucket configuration. Returns new instance with a
     * {@link ObjectStorageEvent.BucketConfigurationChanged} event.
     */
    public Bucket withConfiguration(Configuration config) {
        Objects.requireNonNull(config);
        var newEvents = appendEvent(
            new ObjectStorageEvent.BucketConfigurationChanged(id, config, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            config, newEvents);
    }

    /**
     * Returns the accumulated domain events since the last {@link #clearEvents()}.
     */
    public List<ObjectStorageEvent> domainEvents() {
        return events;
    }

    /**
     * Returns a new Bucket with all events cleared — suitable for persistence.
     */
    public Bucket clearEvents() {
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            configuration, List.of());
    }

    private List<ObjectStorageEvent> appendEvent(ObjectStorageEvent event) {
        var newEvents = new ArrayList<ObjectStorageEvent>(events.size() + 1);
        newEvents.addAll(events);
        newEvents.add(event);
        return Collections.unmodifiableList(newEvents);
    }

    // ── Nested value object ──

    /**
     * Bucket configuration — value object inside the Bucket aggregate.
     * Holds CORS rules.
     * Immutable, equality by fields.
     */
    public record Configuration(
        List<CorsRule> corsRules
    ) {
        public Configuration {
            if (corsRules != null) {
                corsRules = List.copyOf(corsRules);
            }
        }

        public record CorsRule(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            int maxAgeSeconds,
            List<String> exposeHeaders,
            String id
        ) {
            public CorsRule {
                Objects.requireNonNull(allowedOrigins);
                Objects.requireNonNull(allowedMethods);
                allowedOrigins = List.copyOf(allowedOrigins);
                allowedMethods = List.copyOf(allowedMethods);
                if (allowedHeaders != null) {
                    allowedHeaders = List.copyOf(allowedHeaders);
                }
                if (exposeHeaders != null) {
                    exposeHeaders = List.copyOf(exposeHeaders);
                }
            }
        }

        public boolean hasCors() {
            return corsRules != null && !corsRules.isEmpty();
        }
    }
}
