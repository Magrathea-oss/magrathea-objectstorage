package com.example.magrathea.s3api.adapter.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Computes S3-compatible ETag values.
 * For single-part uploads, the ETag is the quoted lowercase MD5 hex of the full object body.
 * For multipart uploads, the ETag is the quoted MD5 of the concatenated part ETags
 * (unquoted hex bytes) + "-{partCount}".
 */
public final class ETagComputer {

    private ETagComputer() {}

    /**
     * Compute the quoted MD5 ETag of a byte array body.
     * Result format: {@code "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"} (32 hex chars enclosed in double quotes).
     */
    public static String computeETag(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(body);
            return "\"" + toHex(digest) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    /**
     * Compute the multipart ETag.
     * Input: list of per-part ETags (each is a quoted MD5 hex string like {@code "aabbcc..."}).
     * Result: MD5(concatenation of unquoted part ETags as hex bytes) + "-{partCount}".
     * Format: {@code "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx-N"}.
     */
    public static String computeMultipartETag(List<String> partETags) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (String partETag : partETags) {
                String hex = partETag.replace("\"", "");
                byte[] partBytes = hexToBytes(hex);
                md.update(partBytes);
            }
            byte[] digest = md.digest();
            return "\"" + toHex(digest) + "-" + partETags.size() + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
