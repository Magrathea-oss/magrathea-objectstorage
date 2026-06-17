package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;

/**
 * Application port — checksum/fingerprint calculations.
 * This is a synchronous port (no Mono) because checksumming is CPU-bound
 * and operates on already-materialized byte arrays.
 *
 * <p><b>Status:</b> superseded in the reactive pipeline path.
 * The {@code ReactiveStorageOrchestrator} no longer injects this port; fingerprints
 * are produced directly by each {@link com.example.magrathea.storageengine.application.pipeline.DataProcessingStep}
 * and carried on the {@link com.example.magrathea.storageengine.application.pipeline.StorageTrace}.
 * This interface is retained as a declared Spring bean for potential future use
 * (e.g. external checksum verification or admin-layer integrity checks)
 * but callers should prefer computing fingerprints inside pipeline steps.
 *
 * @deprecated Use pipeline-level fingerprinting via StorageTrace instead of injecting
 *             this port into orchestration services.
 */
@Deprecated(since = "reactive-pipeline", forRemoval = false)
public interface ChecksumPort {
    Fingerprint fingerprint(byte[] data, FingerprintAlgorithm algorithm);
    ContentHash calculate(byte[] data, ChecksumAlgorithm algorithm);
    boolean verify(byte[] data, ContentHash expected);
}
