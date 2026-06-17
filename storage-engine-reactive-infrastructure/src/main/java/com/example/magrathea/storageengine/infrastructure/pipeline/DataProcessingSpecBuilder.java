package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.domain.pipeline.DataProcessingSpec;
import com.example.magrathea.storageengine.domain.pipeline.StepSpec;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives a DataProcessingSpec from an EffectiveStoragePolicy.
 * Only includes steps that are configured (present Optional) in the policy.
 * Fixed order: Dedup → Compress → Encrypt → EC.
 *
 * Note: EffectiveStoragePolicy.encryption() returns Optional&lt;EncryptionPolicy&gt;.
 * StepSpec.Encrypt requires EncryptionConfig. The conversion is:
 *   new EncryptionConfig(ep.algorithm(), ep.defaultKeyReference())
 */
public class DataProcessingSpecBuilder {

    public DataProcessingSpec build(EffectiveStoragePolicy policy) {
        List<StepSpec> steps = new ArrayList<>();

        policy.dedup().ifPresent(d ->
            steps.add(new StepSpec.Dedup(d)));

        policy.compression().ifPresent(c ->
            steps.add(new StepSpec.Compress(c)));

        policy.encryption().ifPresent(ep ->
            steps.add(new StepSpec.Encrypt(
                new EncryptionConfig(ep.algorithm(), ep.defaultKeyReference()))));

        policy.erasureCoding().ifPresent(ec ->
            steps.add(new StepSpec.EC(ec)));

        return new DataProcessingSpec(steps);
    }
}
