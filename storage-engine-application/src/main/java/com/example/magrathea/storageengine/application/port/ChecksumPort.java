package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;

/**
 * Application port — checksum/fingerprint calculations.
 * This is a synchronous port (no Mono) because checksumming is CPU-bound
 * and operates on already-materialized byte arrays.
 */
public interface ChecksumPort {
    Fingerprint fingerprint(byte[] data, FingerprintAlgorithm algorithm);
    ContentHash calculate(byte[] data, ChecksumAlgorithm algorithm);
    boolean verify(byte[] data, ContentHash expected);
}
