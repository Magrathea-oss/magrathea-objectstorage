package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AbacConfiguration — attribute-based access control rules for a bucket.
 * <p>
 * Each rule grants an {@code action} to a {@code principal} on a {@code resource},
 * optionally gated by tag-based {@link Condition}s. This mirrors the shape exposed by
 * the S3-compatible bucket ABAC configuration API so that the aggregate can persist and
 * return exactly what the API accepts.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record AbacConfiguration(
    List<AbacRule> rules
) {

    public AbacConfiguration {
        Objects.requireNonNull(rules);
        rules = List.copyOf(rules);
    }

    public static AbacConfiguration of(List<AbacRule> rules) {
        return new AbacConfiguration(rules);
    }

    public static AbacConfiguration empty() {
        return new AbacConfiguration(List.of());
    }

    public List<AbacRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * An individual ABAC rule: grant {@code action} to {@code principal} on
     * {@code resource}, gated by optional tag conditions.
     */
    public record AbacRule(
        String id,
        String principal,
        String resource,
        String action,
        List<Condition> conditions
    ) {

        public AbacRule {
            Objects.requireNonNull(action);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public static AbacRule of(String id, String principal, String resource, String action,
                                  List<Condition> conditions) {
            return new AbacRule(id, principal, resource, action, conditions);
        }

        public List<Condition> conditions() {
            return Collections.unmodifiableList(conditions);
        }
    }

    /** A tag-based condition: {@code tag} must equal {@code value}. */
    public record Condition(String tag, String value) {
        public static Condition of(String tag, String value) {
            return new Condition(tag, value);
        }
    }
}
