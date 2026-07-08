package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency unit tests for the atomic read-compose-write reference commit of
 * {@link S3ObjectManifestReferenceStore} (REQ-FS-006 defect regression: torn S3 object
 * reference under concurrent same-key PUTs).
 *
 * <p>These are pure JUnit tests (no Spring context) against the real filesystem store
 * in a temporary directory. They verify:</p>
 * <ul>
 *   <li>concurrent same-key commits always leave a single self-consistent reference —
 *       versionId, manifestId, etag, and size all belong to ONE winning commit and are
 *       never mixed across commits;</li>
 *   <li>concurrent readers never observe a torn or partially written reference while
 *       same-key commits are racing (temp-file + atomic-move contract);</li>
 *   <li>the read-compose-write cycle is serialized per key (no lost updates);</li>
 *   <li>unrelated keys committed concurrently with a contended hot key are never
 *       corrupted or cross-contaminated.</li>
 * </ul>
 */
class S3ObjectManifestReferenceStoreConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS_PER_THREAD = 50;
    private static final String BUCKET = "fs-concurrency-bucket";
    private static final String HOT_KEY = "concurrent/2026/put/object.bin";
    private static final ZonedDateTime FIXED_CREATED_AT =
        ZonedDateTime.of(2026, 7, 2, 12, 0, 0, 0, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void concurrentSameKeyCommitsAlwaysLeaveOneSelfConsistentReference() throws Exception {
        S3ObjectManifestReferenceStore store = newStore();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS + 1);
        try {
            CyclicBarrier startBarrier = new CyclicBarrier(THREADS);
            AtomicBoolean writersDone = new AtomicBoolean(false);
            List<Future<?>> writers = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                int threadId = thread;
                writers.add(executor.submit(() -> {
                    awaitQuietly(startBarrier);
                    for (int iteration = 0; iteration < ITERATIONS_PER_THREAD; iteration++) {
                        S3ObjectManifestReferenceStore.Reference next =
                            selfConsistentReference(BUCKET, HOT_KEY, threadId, iteration);
                        store.commitLatest(BUCKET, HOT_KEY, current -> Optional.of(next)).block();
                    }
                    return null;
                }));
            }

            // Concurrent reader: while same-key commits are racing, every observed
            // reference must already be complete and self-consistent — never torn.
            Future<Integer> reader = executor.submit(() -> {
                int observed = 0;
                while (!writersDone.get()) {
                    Optional<S3ObjectManifestReferenceStore.Reference> current =
                        store.find(BUCKET, HOT_KEY).block();
                    if (current != null && current.isPresent()) {
                        assertSelfConsistent(current.get());
                        observed++;
                    }
                }
                return observed;
            });

            for (Future<?> writer : writers) {
                writer.get(120, TimeUnit.SECONDS);
            }
            writersDone.set(true);
            int observedDuringRace = reader.get(30, TimeUnit.SECONDS);
            assertTrue(observedDuringRace > 0,
                "reader must observe committed references while commits are racing");

            Optional<S3ObjectManifestReferenceStore.Reference> winner =
                store.find(BUCKET, HOT_KEY).block();
            assertTrue(winner != null && winner.isPresent(),
                "a winning reference must be committed after all concurrent commits");
            assertSelfConsistent(winner.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void readComposeWriteIsSerializedPerKeyWithoutLostUpdates() throws Exception {
        S3ObjectManifestReferenceStore store = newStore();
        // Seed with size 0; every commit increments the currently committed size by one.
        S3ObjectManifestReferenceStore.Reference seed =
            withSize(selfConsistentReference(BUCKET, HOT_KEY, 0, 0), 0);
        store.commitLatest(BUCKET, HOT_KEY, current -> Optional.of(seed)).block();

        int increments = 100;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CyclicBarrier startBarrier = new CyclicBarrier(THREADS);
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                futures.add(executor.submit(() -> {
                    awaitQuietly(startBarrier);
                    for (int i = 0; i < increments; i++) {
                        store.commitLatest(BUCKET, HOT_KEY, current ->
                            Optional.of(withSize(
                                current.orElseThrow(),
                                current.orElseThrow().size() + 1))).block();
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        long finalSize = store.find(BUCKET, HOT_KEY).block().orElseThrow().size();
        assertEquals((long) THREADS * increments, finalSize,
            "read-compose-write must be serialized per key: no increment may be lost");
    }

    @Test
    void unrelatedKeysAreNotCorruptedWhileAHotKeyIsContended() throws Exception {
        S3ObjectManifestReferenceStore store = newStore();
        int distinctKeys = THREADS;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS * 2);
        try {
            CyclicBarrier startBarrier = new CyclicBarrier(THREADS * 2);
            CountDownLatch done = new CountDownLatch(THREADS * 2);
            List<Future<?>> futures = new ArrayList<>();

            // Contending writers on the hot key.
            for (int thread = 0; thread < THREADS; thread++) {
                int threadId = thread;
                futures.add(executor.submit(() -> {
                    awaitQuietly(startBarrier);
                    try {
                        for (int iteration = 0; iteration < ITERATIONS_PER_THREAD; iteration++) {
                            int commitIteration = iteration;
                            store.commitLatest(BUCKET, HOT_KEY, current -> Optional.of(
                                selfConsistentReference(
                                    BUCKET, HOT_KEY, threadId, commitIteration))).block();
                        }
                    } finally {
                        done.countDown();
                    }
                    return null;
                }));
            }

            // One writer per unrelated key, racing alongside the hot-key contention.
            for (int thread = 0; thread < distinctKeys; thread++) {
                int threadId = thread;
                String key = "concurrent/2026/put/unrelated-object-" + threadId + ".bin";
                futures.add(executor.submit(() -> {
                    awaitQuietly(startBarrier);
                    try {
                        for (int iteration = 0; iteration < ITERATIONS_PER_THREAD; iteration++) {
                            int commitIteration = iteration;
                            store.commitLatest(BUCKET, key, current -> Optional.of(
                                selfConsistentReference(
                                    BUCKET, key, threadId, commitIteration))).block();
                        }
                    } finally {
                        done.countDown();
                    }
                    return null;
                }));
            }

            assertTrue(done.await(120, TimeUnit.SECONDS), "all concurrent writers must finish");
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Every unrelated key must hold exactly its own final, self-consistent reference.
        for (int thread = 0; thread < distinctKeys; thread++) {
            String key = "concurrent/2026/put/unrelated-object-" + thread + ".bin";
            S3ObjectManifestReferenceStore.Reference reference =
                store.find(BUCKET, key).block().orElseThrow();
            assertEquals(key, reference.key(), "reference must belong to its own key");
            assertSelfConsistent(reference);
            assertEquals(
                selfConsistentReference(BUCKET, key, thread, ITERATIONS_PER_THREAD - 1),
                reference,
                "unrelated key must end with its own last commit, uncorrupted by hot-key contention");
        }

        // The hot key itself must also end self-consistent.
        assertSelfConsistent(store.find(BUCKET, HOT_KEY).block().orElseThrow());
    }

    // ── helpers ──

    private S3ObjectManifestReferenceStore newStore() {
        return new S3ObjectManifestReferenceStore(
            tempDir.resolve("metadata").resolve("s3-object-references"));
    }

    /**
     * Builds a reference whose versionId, manifestId, etag, size, and user metadata are
     * all deterministically derived from one (bucket, key, thread, iteration) commit
     * token, so any field mixed in from a different commit is detectable.
     */
    private static S3ObjectManifestReferenceStore.Reference selfConsistentReference(
            String bucket, String key, int thread, int iteration) {
        String token = commitToken(bucket, key, thread, iteration);
        return new S3ObjectManifestReferenceStore.Reference(
            bucket,
            key,
            "STANDARD",
            expectedEtag(token),
            Map.of("commit-token", token),
            Map.of(),
            expectedSize(token),
            expectedManifestId(token),
            VersionId.of("v-" + token),
            FIXED_CREATED_AT);
    }

    private static void assertSelfConsistent(S3ObjectManifestReferenceStore.Reference reference) {
        String versionId = reference.versionId().value();
        assertTrue(versionId.startsWith("v-"),
            "versionId must carry a commit token, got: " + versionId);
        String token = versionId.substring(2);
        assertEquals(expectedManifestId(token), reference.manifestId(),
            "manifestId must come from the same commit as versionId " + versionId);
        assertEquals(expectedEtag(token), reference.etag(),
            "etag must come from the same commit as versionId " + versionId);
        assertEquals(expectedSize(token), reference.size(),
            "size must come from the same commit as versionId " + versionId);
        assertEquals(token, reference.userMetadata().get("commit-token"),
            "user metadata must come from the same commit as versionId " + versionId);
    }

    private static S3ObjectManifestReferenceStore.Reference withSize(
            S3ObjectManifestReferenceStore.Reference reference, long size) {
        return new S3ObjectManifestReferenceStore.Reference(
            reference.bucket(),
            reference.key(),
            reference.storageClass(),
            reference.etag(),
            reference.userMetadata(),
            reference.objectTags(),
            size,
            reference.manifestId(),
            reference.versionId(),
            reference.createdAt());
    }

    private static String commitToken(String bucket, String key, int thread, int iteration) {
        return bucket + ":" + key + ":t" + thread + ":i" + iteration;
    }

    private static ManifestId expectedManifestId(String token) {
        return ManifestId.of(UUID.nameUUIDFromBytes(
            ("manifest:" + token).getBytes(StandardCharsets.UTF_8)));
    }

    private static String expectedEtag(String token) {
        return "\"" + UUID.nameUUIDFromBytes(
            ("etag:" + token).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "") + "\"";
    }

    private static long expectedSize(String token) {
        return Math.floorMod(("size:" + token).hashCode(), 1_000_000);
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Barrier wait interrupted", e);
        }
    }
}
