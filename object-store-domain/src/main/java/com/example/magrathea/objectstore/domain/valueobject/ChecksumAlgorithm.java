package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Arrays;
import java.util.Optional;

/**
 * All checksum algorithms supported by AWS S3.
 * Maps to x-amz-sdk-checksum-algorithm and x-amz-checksum-&lt;alg&gt; headers.
 */
public enum ChecksumAlgorithm {
    CRC32("crc32"),
    CRC32C("crc32c"),
    CRC64NVME("crc64nvme"),
    SHA1("sha1"),
    SHA256("sha256"),
    SHA512("sha512"),
    XXHASH64("xxhash64"),
    XXHASH3("xxhash3"),
    XXHASH128("xxhash128"),
    MD5("md5"); // legacy Content-MD5

    private final String apiName;

    ChecksumAlgorithm(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    /**
     * Resolve from AWS API header value (e.g., "sha256", "crc64nvme").
     * Returns empty for unknown/invalid algorithm names.
     */
    public static Optional<ChecksumAlgorithm> fromApiName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(algorithm -> algorithm.apiName.equals(name))
            .findFirst();
    }
}
