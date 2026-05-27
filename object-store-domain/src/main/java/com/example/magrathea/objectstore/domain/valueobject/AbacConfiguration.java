package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AbacConfiguration — a value object representing attribute-based access control rules for a bucket.
 * <p>
 * ABAC (Attribute-Based Access Control) allows fine-grained access control based on
 * object tags and attributes. Each rule defines a condition that must be satisfied
 * for access to be granted.
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

    /**
     * Factory method — create from a list of rules.
     */
    public static AbacConfiguration of(List<AbacRule> rules) {
        return new AbacConfiguration(rules);
    }

    /**
     * Factory method — empty configuration (no rules).
     */
    public static AbacConfiguration empty() {
        return new AbacConfiguration(List.of());
    }

    public List<AbacRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * An individual ABAC rule — maps an attribute condition to an action.
     */
    public record AbacRule(
        String attribute,
        String operator,
        String value,
        String action
    ) {

        public AbacRule {
            Objects.requireNonNull(attribute);
            Objects.requireNonNull(operator);
            Objects.requireNonNull(value);
            Objects.requireNonNull(action);
            if (attribute.isBlank()) throw new IllegalArgumentException("attribute must not be blank");
            if (operator.isBlank()) throw new IllegalArgumentException("operator must not be blank");
            if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
            if (action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        }

        /**
         * Factory method.
         */
        public static AbacRule of(String attribute, String operator, String value, String action) {
            return new AbacRule(attribute, operator, value, action);
        }
    }
}
