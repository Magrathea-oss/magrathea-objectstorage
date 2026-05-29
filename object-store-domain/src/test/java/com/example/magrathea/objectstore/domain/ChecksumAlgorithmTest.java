package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumAlgorithmTest {

    @Test
    void fromApiName_resolves_known_algorithms() {
        assertEquals(ChecksumAlgorithm.SHA256, ChecksumAlgorithm.fromApiName("sha256").orElseThrow());
        assertEquals(ChecksumAlgorithm.CRC64NVME, ChecksumAlgorithm.fromApiName("crc64nvme").orElseThrow());
        assertEquals(ChecksumAlgorithm.CRC32, ChecksumAlgorithm.fromApiName("crc32").orElseThrow());
        assertEquals(ChecksumAlgorithm.CRC32C, ChecksumAlgorithm.fromApiName("crc32c").orElseThrow());
        assertEquals(ChecksumAlgorithm.SHA1, ChecksumAlgorithm.fromApiName("sha1").orElseThrow());
        assertEquals(ChecksumAlgorithm.SHA512, ChecksumAlgorithm.fromApiName("sha512").orElseThrow());
        assertEquals(ChecksumAlgorithm.XXHASH64, ChecksumAlgorithm.fromApiName("xxhash64").orElseThrow());
        assertEquals(ChecksumAlgorithm.XXHASH3, ChecksumAlgorithm.fromApiName("xxhash3").orElseThrow());
        assertEquals(ChecksumAlgorithm.XXHASH128, ChecksumAlgorithm.fromApiName("xxhash128").orElseThrow());
        assertEquals(ChecksumAlgorithm.MD5, ChecksumAlgorithm.fromApiName("md5").orElseThrow());
    }

    @Test
    void fromApiName_returns_empty_for_unknown() {
        assertTrue(ChecksumAlgorithm.fromApiName("fake-algorithm").isEmpty());
        assertTrue(ChecksumAlgorithm.fromApiName("").isEmpty());
        assertTrue(ChecksumAlgorithm.fromApiName(null).isEmpty());
    }

    @Test
    void checksumValue_rejects_invalid() {
        assertThrows(NullPointerException.class,
            () -> new ChecksumValue(ChecksumAlgorithm.SHA256, null));
        assertThrows(IllegalArgumentException.class,
            () -> new ChecksumValue(ChecksumAlgorithm.SHA256, ""));
        assertThrows(NullPointerException.class,
            () -> new ChecksumValue(null, "base64hash"));
    }

    @Test
    void checksumValue_accepts_valid() {
        var cv = new ChecksumValue(ChecksumAlgorithm.SHA256, "T7TCCRBAsnfGK6f71GQjWflL+m9uMQHQTdKKpVF8KKY=");
        assertEquals(ChecksumAlgorithm.SHA256, cv.algorithm());
        assertEquals("T7TCCRBAsnfGK6f71GQjWflL+m9uMQHQTdKKpVF8KKY=", cv.base64Value());
    }

    @Test
    void apiName_matches_aws_header() {
        assertEquals("sha256", ChecksumAlgorithm.SHA256.apiName());
        assertEquals("crc64nvme", ChecksumAlgorithm.CRC64NVME.apiName());
        assertEquals("md5", ChecksumAlgorithm.MD5.apiName());
    }
}
