package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ChecksumPort;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 based checksum/fingerprint implementation.
 * Uses java.security.MessageDigest for SHA-256.
 */
public class Sha256ChecksumPort implements ChecksumPort {

    @Override
    public Fingerprint fingerprint(byte[] data, FingerprintAlgorithm algorithm) {
        byte[] hash = sha256(data);
        String hex = HexFormat.of().formatHex(hash);
        return Fingerprint.of(FingerprintAlgorithm.SHA256, hex);
    }

    @Override
    public ContentHash calculate(byte[] data, ChecksumAlgorithm algorithm) {
        byte[] hash = sha256(data);
        String hex = HexFormat.of().formatHex(hash);
        return ContentHash.of(ChecksumAlgorithm.SHA256, hex);
    }

    @Override
    public boolean verify(byte[] data, ContentHash expected) {
        ContentHash actual = calculate(data, expected.algorithm());
        return actual.value().equals(expected.value());
    }

    private byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
