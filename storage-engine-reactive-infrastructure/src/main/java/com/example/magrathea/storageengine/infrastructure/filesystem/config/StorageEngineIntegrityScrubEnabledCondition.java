package com.example.magrathea.storageengine.infrastructure.filesystem.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Property condition kept Spring-only so the infrastructure module does not require Boot autoconfigure. */
public final class StorageEngineIntegrityScrubEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty(
                "storage.engine.integrity.scrub.enabled", Boolean.class, false);
    }
}
