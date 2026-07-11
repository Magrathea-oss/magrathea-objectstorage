package com.example.magrathea.storageengine.infrastructure.filesystem.config;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the periodic integrity scrub scheduler only when explicitly configured. */
@Configuration
@Profile("storage-engine")
@EnableScheduling
@Conditional(StorageEngineIntegrityScrubEnabledCondition.class)
public class StorageEngineIntegrityScrubSchedulingConfig {
}
