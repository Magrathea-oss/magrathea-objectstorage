package com.example.magrathea.s3api.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Local durable key-management service for the EP-1 single-node production profile.
 *
 * <p>The key file contains a Base64 encoded 256-bit AES key. The file is created once
 * and then reused across process restarts so encrypted credentials and SSE object
 * bytes remain decryptable without embedding constants in code.</p>
 */
public final class LocalS3KeyManagementService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AES = "AES";
    private static final String AES_CTR = "AES/CTR/NoPadding";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final Path keyFile;
    private final SecretKey masterKey;

    public LocalS3KeyManagementService(Path keyFile) {
        this.keyFile = keyFile;
        this.masterKey = new SecretKeySpec(loadOrCreateKey(keyFile), AES);
    }

    public String encryptSecret(String plaintext) {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        byte[] cipher = crypt(plaintext.getBytes(StandardCharsets.UTF_8), nonce);
        ByteBuffer buffer = ByteBuffer.allocate(nonce.length + cipher.length);
        buffer.put(nonce).put(cipher);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public String decryptSecret(String encodedCiphertext) {
        byte[] packed = Base64.getDecoder().decode(encodedCiphertext);
        if (packed.length < 17) {
            throw new IllegalArgumentException("Encrypted secret is malformed");
        }
        byte[] nonce = Arrays.copyOfRange(packed, 0, 16);
        byte[] cipher = Arrays.copyOfRange(packed, 16, packed.length);
        return new String(crypt(cipher, nonce), StandardCharsets.UTF_8);
    }

    public byte[] encryptObject(String bucket, String key, byte[] plaintext) {
        return crypt(plaintext, objectNonce(bucket, key));
    }

    public byte[] decryptObject(String bucket, String key, byte[] ciphertext) {
        return crypt(ciphertext, objectNonce(bucket, key));
    }

    public Path keyFile() {
        return keyFile;
    }

    private byte[] crypt(byte[] input, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance(AES_CTR);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new IvParameterSpec(nonce));
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply local S3 key-management operation", e);
        }
    }

    private byte[] objectNonce(String bucket, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(masterKey.getEncoded(), HMAC_SHA256));
            byte[] digest = mac.doFinal((bucket + "/" + key).getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive object encryption nonce", e);
        }
    }

    private static byte[] loadOrCreateKey(Path keyFile) {
        try {
            Path parent = keyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(keyFile)) {
                return Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim());
            }
            byte[] key = new byte[32];
            RANDOM.nextBytes(key);
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(key), StandardCharsets.UTF_8);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load or create local S3 master key " + keyFile, e);
        }
    }

    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(masterKey.getEncoded()));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
