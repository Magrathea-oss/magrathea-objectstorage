package com.example.magrathea.s3api.phase2awscli;

import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

/**
 * Real HTTP test application for Phase 2 storage-engine AWS CLI requirements.
 */
@SpringBootApplication
@ComponentScan({
    "com.example.magrathea.objectstore",
    "com.example.magrathea.reactive",
    "com.example.magrathea.objectstorage.repository.storageengine",
    "com.example.magrathea.storageengine"
})
public class Phase2StorageEngineAwsCliTestApp {

    @Bean
    public AwsCliSharedContext awsCliSharedContext() {
        return new AwsCliSharedContext();
    }

    /**
     * Override the default EffectivePolicyResolver with a test-safe variant that respects
     * the base storage policy's encryption setting. When the base policy has encryption
     * disabled, SSE-S3/SSE-KMS requests are recorded as metadata only (config-only mode)
     * and chunk-level encryption is not applied. This prevents the read path from returning
     * unreadable encrypted bytes in tests where SSE enforcement is intentionally deferred.
     */
    @Bean
    @Primary
    public EffectivePolicyResolver requirementsSafeEffectivePolicyResolver() {
        return new EffectivePolicyResolver() {
            @Override
            public EffectiveStoragePolicy resolve(StoragePolicy policy, UploadRequestContext context) {
                EffectiveStoragePolicy effective = super.resolve(policy, context);
                // Config-only SSE: respect the base policy's encryption setting.
                // If the base policy has encryption disabled, do not enable chunk-level
                // encryption even when an SSE header is present in the request.
                if (policy.encryption().isEmpty() && effective.encryption().isPresent()) {
                    return EffectiveStoragePolicy.of(
                            effective.storageClassId(),
                            effective.bucketRef(),
                            effective.dedup(),
                            effective.compression(),
                            java.util.Optional.empty(),
                            effective.erasureCoding(),
                            effective.replication());
                }
                return effective;
            }
        };
    }
}
