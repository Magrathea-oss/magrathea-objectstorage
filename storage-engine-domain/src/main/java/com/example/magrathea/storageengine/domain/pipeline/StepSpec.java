package com.example.magrathea.storageengine.domain.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionConfig;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;

/**
 * Sealed specification of a single processing step.
 * Expresses WHAT to do and with which config — not HOW to execute it.
 * The execution contract lives in the application layer (DataProcessingStep).
 *
 * Step order is fixed by convention: Dedup → Compress → Encrypt → EC.
 * Steps are optional; absent steps are simply not included in DataProcessingSpec.
 */
public sealed interface StepSpec {

    record Dedup(DedupConfig config)          implements StepSpec {}
    record Compress(CompressionConfig config) implements StepSpec {}
    record Encrypt(EncryptionConfig config)   implements StepSpec {}
    record EC(ErasureCodingConfig config)     implements StepSpec {}
}
