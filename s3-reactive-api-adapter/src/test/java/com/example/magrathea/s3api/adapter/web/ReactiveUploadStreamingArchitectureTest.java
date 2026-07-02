package com.example.magrathea.s3api.adapter.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveUploadStreamingArchitectureTest {

    @Test
    void putObjectMustTeeRequestBodyForEtagAndStorageWithoutWholeBodyJoin() throws IOException {
        var source = Files.readString(sourcePath(
            "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3ObjectOperationsHandler.java"));
        var putObject = methodBody(source,
            "public Mono<ServerResponse> putObject(ServerRequest request)");

        assertFalse(putObject.contains("DataBufferUtils.join("),
            "PutObject must not use DataBufferUtils.join to compute ETag/checksum; "
                + "it must tee the DataBuffer stream while storing the request body.");
        assertFalse(putObject.contains("new byte[joined.readableByteCount()]"),
            "PutObject must not materialize the full joined request body into a byte array before storage.");
        assertFalse(putObject.contains("ETagComputer.computeETag(bytes)"),
            "PutObject must compute the ETag while teeing the stream, not from a joined whole-object byte array.");
        assertFalse(putObject.contains("Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))"),
            "PutObject must pass a streaming body to storage, not a re-wrapped whole-object byte array.");
    }

    @Test
    void fixedWindowDedupMustProcessDataBuffersIncrementallyWithoutJoiningFileUnit() throws IOException {
        var source = Files.readString(sourcePath(
            "storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/pipeline/FixedWindowDedupStep.java"));

        assertFalse(source.contains("DataBufferUtils.join("),
            "FixedWindowDedupStep must not use DataBufferUtils.join on a FileUnit; "
                + "deduplication must consume DataBuffers incrementally into configured windows.");
        assertFalse(source.contains("byte[] allBytes"),
            "FixedWindowDedupStep must not materialize the whole FileUnit into one byte array.");
        assertFalse(source.contains("splitIntoWindows(allBytes"),
            "FixedWindowDedupStep must emit configured windows while streaming, not after joining the whole FileUnit.");
    }

    private static Path sourcePath(String relativePath) {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find " + relativePath + " from working directory "
            + Path.of("").toAbsolutePath());
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "Expected method signature not found: " + signature);
        int braceStart = source.indexOf('{', signatureStart);
        assertTrue(braceStart >= 0, "Expected method opening brace not found: " + signature);

        int depth = 0;
        for (int index = braceStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, index + 1);
                }
            }
        }
        throw new IllegalStateException("Could not extract method body for " + signature);
    }
}
