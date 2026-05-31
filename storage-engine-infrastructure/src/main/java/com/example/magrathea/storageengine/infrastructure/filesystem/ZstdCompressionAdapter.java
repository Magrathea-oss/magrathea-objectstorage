package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Compression adapter using Java's built-in Deflater (simulating ZSTD).
 * In production, this would use the ZSTD library via JNI or a native binding.
 */
public class ZstdCompressionAdapter {

    /**
     * Compresses data using Deflater (simulated ZSTD).
     *
     * @param data   the uncompressed data
     * @param config compression configuration
     * @return compressed data
     */
    public byte[] compress(byte[] data, CompressionConfig config) {
        try {
            Deflater deflater = new Deflater(config.level());
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                baos.write(buf, 0, n);
            }
            deflater.end();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Compression failed", e);
        }
    }

    /**
     * Decompresses data using Inflater (simulated ZSTD).
     *
     * @param compressed compressed data
     * @return decompressed data
     */
    public byte[] decompress(byte[] compressed) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                baos.write(buf, 0, n);
            }
            inflater.end();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Decompression failed", e);
        }
    }
}
