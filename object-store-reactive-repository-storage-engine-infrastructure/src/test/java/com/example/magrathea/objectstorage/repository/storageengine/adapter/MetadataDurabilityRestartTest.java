package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketJournalTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EP-2 metadata durability: bucket registry, multipart upload state and
 * per-object configuration must survive a process restart.
 *
 * <p>A restart is simulated by constructing a NEW store instance over the same
 * storage root — exactly what happens when the JVM restarts and Spring re-creates
 * the storage-engine repositories.</p>
 */
class MetadataDurabilityRestartTest {

    @TempDir
    Path storageRoot;

    @Nested
    @DisplayName("Bucket registry durability (REQ-EP2 bucket registry)")
    class BucketRegistry {

        @Test
        @DisplayName("a created bucket with configuration families survives a restart")
        void bucketSurvivesRestart() {
            Path root = storageRoot.resolve("metadata").resolve("buckets");
            BucketStore store = new BucketStore(root);

            var cors = new CorsConfiguration(List.of(new CorsConfiguration.CorsRule(
                List.of("https://app.example.com"),
                List.of("GET", "PUT"),
                List.of("x-amz-meta-owner"),
                3600,
                List.of("ETag"),
                "cors-rule-1")));
            var abac = AbacConfiguration.of(List.of(
                AbacConfiguration.AbacRule.of("rule-1", "user/admin", "arn:aws:s3:::durable-reports/*",
                    "s3:GetObject",
                    List.of(AbacConfiguration.Condition.of("role", "admin")))));
            var config = BucketConfig.EMPTY
                .withCorsConfiguration(cors)
                .withAbacConfiguration(abac);
            var bucket = Bucket.restore(
                Bucket.Id.of("bucket-id-ep2"), "durable-reports",
                Region.US_EAST_1, StorageClass.STANDARD,
                true, false, false, config);

            store.save(bucket);

            // Simulated restart: fresh store instance over the same directory.
            BucketStore restarted = new BucketStore(root);

            Bucket reloaded = restarted.findByName("durable-reports").orElseThrow();
            assertEquals("bucket-id-ep2", reloaded.id().value());
            assertEquals("us-east-1", reloaded.region().id());
            assertEquals("STANDARD", reloaded.storageClass().name());
            assertTrue(reloaded.versioningEnabled(), "versioning flag must survive restart");
            assertEquals(cors, reloaded.bucketConfig().getCorsConfiguration().orElseThrow(),
                "CORS configuration must survive restart");
            assertEquals(abac, reloaded.bucketConfig().getAbacConfiguration().orElseThrow(),
                "ABAC configuration must survive restart");
        }

        @Test
        @DisplayName("a deleted bucket stays deleted after a restart")
        void deletedBucketStaysDeleted() {
            Path root = storageRoot.resolve("metadata").resolve("buckets");
            BucketStore store = new BucketStore(root);
            store.save(Bucket.restore(
                Bucket.Id.of("bucket-id-gone"), "transient-bucket",
                Region.US_EAST_1, StorageClass.STANDARD, false, false));
            store.delete("transient-bucket");

            BucketStore restarted = new BucketStore(root);
            assertTrue(restarted.findByName("transient-bucket").isEmpty(),
                "deleted bucket must not reappear after restart");
        }
    }

    @Nested
    @DisplayName("Bucket configuration families durability (REQ-DUR-004)")
    class BucketConfigFamilies {

        @Test
        @DisplayName("object-lock, inventory-table and journal-table bucket config survive a restart")
        void bucketConfigFamiliesSurviveRestart() {
            Path root = storageRoot.resolve("metadata").resolve("buckets");
            BucketStore store = new BucketStore(root);

            var config = BucketConfig.EMPTY
                .withObjectLockConfiguration(BucketObjectLockConfiguration.of("GOVERNANCE", 30))
                .withInventoryTableConfiguration(
                    BucketInventoryTableConfiguration.of("inv-1", "Parquet", "Daily", true))
                .withJournalTableConfiguration(
                    BucketJournalTableConfiguration.of("jrn-1", "Parquet", "Hourly", true))
                .withMetadataConfiguration(BucketMetadataConfiguration.of(List.of(
                    BucketMetadataConfiguration.MetadataRule.of(
                        "md-1", "Enabled", "OBJECT", "TAGS"))))
                .withMetadataTableConfiguration(BucketMetadataTableConfiguration.of(List.of(
                    BucketMetadataTableConfiguration.MetadataTableRule.of(
                        "mdt-1", "Enabled", "ep2-metadata-table", "analytics-db"))));
            store.save(Bucket.restore(
                Bucket.Id.of("bucket-id-config"), "ep2-bucket-config-test",
                Region.US_EAST_1, StorageClass.STANDARD, false, false, false, config));

            BucketStore restarted = new BucketStore(root);
            var reloaded = restarted.findByName("ep2-bucket-config-test").orElseThrow().bucketConfig();

            var lock = reloaded.getObjectLockConfiguration().orElseThrow();
            assertTrue(lock.enabled(), "object-lock enabled flag must survive restart");
            assertEquals("GOVERNANCE", lock.mode());
            assertEquals(30, lock.days());
            assertEquals("inv-1", reloaded.getInventoryTableConfiguration().orElseThrow().id(),
                "inventory-table configuration must survive restart");
            assertEquals("jrn-1", reloaded.getJournalTableConfiguration().orElseThrow().id(),
                "journal-table configuration must survive restart");
            assertEquals("Hourly",
                reloaded.getJournalTableConfiguration().orElseThrow().scheduleFrequency());
            assertEquals("md-1",
                reloaded.getMetadataConfiguration().orElseThrow().rules().get(0).id(),
                "metadata configuration must survive restart");
            assertEquals("ep2-metadata-table",
                reloaded.getMetadataTableConfiguration().orElseThrow().rules().get(0).metadataTableName(),
                "metadata-table configuration must survive restart");
        }
    }

    @Nested
    @DisplayName("Multipart upload state durability (REQ-EP2 multipart state)")
    class MultipartState {

        @Test
        @DisplayName("an in-progress multipart upload with recorded parts survives a restart")
        void multipartUploadSurvivesRestart() {
            Path root = storageRoot.resolve("metadata").resolve("multipart-uploads");
            MultipartUploadStateStore store = new MultipartUploadStateStore(root);

            Instant initiated = Instant.parse("2026-07-02T08:00:00Z");
            var upload = MultipartUpload.restore(
                MultipartUpload.Id.of("mpu-id-1"),
                Bucket.Id.of("bucket-id-ep2"),
                ObjectKey.of("durable-reports", "backups/2026-07/archive.tar"),
                UploadId.of("uploadEp2Part42"),
                initiated,
                List.of(
                    UploadPart.of(PartNumber.of(1), "\"etag-part-1\"", 5_242_880L,
                        Instant.parse("2026-07-02T08:01:00Z")),
                    UploadPart.of(PartNumber.of(2), "\"etag-part-2\"", 1_048_576L,
                        Instant.parse("2026-07-02T08:02:00Z"))),
                false, false);
            store.save(upload);

            MultipartUploadStateStore restarted = new MultipartUploadStateStore(root);

            MultipartUpload reloaded = restarted.findById(UploadId.of("uploadEp2Part42")).orElseThrow();
            assertEquals("durable-reports", reloaded.key().bucket());
            assertEquals("backups/2026-07/archive.tar", reloaded.key().key());
            assertEquals(initiated, reloaded.initiated());
            assertEquals(2, reloaded.parts().size(), "recorded parts must survive restart");
            assertEquals("\"etag-part-1\"", reloaded.parts().get(0).etag());
            assertEquals(5_242_880L, reloaded.parts().get(0).size());
            assertEquals(2, reloaded.parts().get(1).partNumber().value());
        }

        @Test
        @DisplayName("an aborted multipart upload removed from the store stays removed after restart")
        void abortedUploadStaysRemoved() {
            Path root = storageRoot.resolve("metadata").resolve("multipart-uploads");
            MultipartUploadStateStore store = new MultipartUploadStateStore(root);
            var upload = MultipartUpload.restore(
                MultipartUpload.Id.of("mpu-id-2"), Bucket.Id.of("bucket-id-ep2"),
                ObjectKey.of("durable-reports", "tmp/aborted.bin"),
                UploadId.of("uploadEp2Aborted"), Instant.now(), List.of(), false, false);
            store.save(upload);
            store.delete(UploadId.of("uploadEp2Aborted"));

            MultipartUploadStateStore restarted = new MultipartUploadStateStore(root);
            assertTrue(restarted.findById(UploadId.of("uploadEp2Aborted")).isEmpty());
        }
    }

    @Nested
    @DisplayName("Object tags durability via the manifest reference (REQ-DUR-003 tags)")
    class ObjectTags {

        @Test
        @DisplayName("object tags recorded in the committed reference survive a restart")
        void objectTagsSurviveRestart() {
            Path root = storageRoot.resolve("metadata").resolve("s3-object-references");
            S3ObjectManifestReferenceStore store = new S3ObjectManifestReferenceStore(root);

            var reference = new S3ObjectManifestReferenceStore.Reference(
                "durable-reports",
                "documents/tags-doc.pdf",
                "STANDARD",
                "\"etag-tags-1\"",
                java.util.Map.of("x-amz-meta-owner", "storage-team"),
                java.util.Map.of("Project", "Alpha", "Version", "2"),
                1024L,
                ManifestId.of(java.util.UUID.randomUUID()),
                VersionId.of("v1"),
                java.time.ZonedDateTime.parse("2026-07-02T09:00:00Z"));
            store.commitLatest("durable-reports", "documents/tags-doc.pdf",
                current -> java.util.Optional.of(reference)).block();

            // Simulated restart: fresh store instance reads the durable file.
            S3ObjectManifestReferenceStore restarted = new S3ObjectManifestReferenceStore(root);
            var reloaded = restarted.find("durable-reports", "documents/tags-doc.pdf")
                .block().orElseThrow();

            assertEquals(java.util.Map.of("Project", "Alpha", "Version", "2"),
                reloaded.objectTags(), "object tags must survive restart in the reference");
            var restored = reloaded.toS3Object();
            assertEquals("Alpha", restored.objectTags().get("Project"),
                "restored aggregate must expose the persisted tags for GetObjectTagging");
            assertEquals(java.util.Map.of("x-amz-meta-owner", "storage-team"),
                restored.userMetadata(), "user metadata must not be mixed with tags");
        }
    }

    @Nested
    @DisplayName("Per-object configuration durability (REQ-EP2 object config)")
    class ObjectConfig {

        @Test
        @DisplayName("legal hold, lock, retention, encryption and restore state survive a restart")
        void objectConfigSurvivesRestart() {
            Path root = storageRoot.resolve("metadata").resolve("object-config");
            ObjectConfigMetadataStore store = new ObjectConfigMetadataStore(root);

            var legalHold = LegalHold.restore(true, Instant.parse("2026-07-02T09:00:00Z"));
            var retention = ObjectLockConfiguration.RetentionPeriod.of(
                Duration.ofDays(365), Instant.parse("2026-07-02T09:00:00Z"));
            var lock = ObjectLockConfiguration.of(
                ObjectLockConfiguration.ObjectLockMode.COMPLIANCE, retention, true);
            var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AES256);
            var restore = RestoreConfiguration.restore(
                Instant.parse("2026-07-02T09:10:00Z"),
                Instant.parse("2026-07-09T09:10:00Z"),
                RestoreConfiguration.RestoreTier.STANDARD);

            String bucket = "durable-reports";
            String key = "records/case-4711/evidence.pdf";
            store.update(bucket, key, config -> config
                .withLegalHold(legalHold)
                .withLockConfiguration(lock)
                .withRetention(retention)
                .withEncryption(encryption)
                .withRestore(restore));

            ObjectConfigMetadataStore restarted = new ObjectConfigMetadataStore(root);

            var reloaded = restarted.find(bucket, key).orElseThrow();
            assertEquals(legalHold, reloaded.legalHold(), "legal hold must survive restart");
            assertEquals(lock, reloaded.lockConfiguration(), "lock config must survive restart");
            assertEquals(retention, reloaded.retention(), "retention must survive restart");
            assertEquals(encryption, reloaded.encryption(), "encryption must survive restart");
            assertEquals(restore, reloaded.restore(), "restore state must survive restart");
        }

        @Test
        @DisplayName("updating one family never loses the other persisted families")
        void partialUpdatePreservesOtherFamilies() {
            Path root = storageRoot.resolve("metadata").resolve("object-config");
            ObjectConfigMetadataStore store = new ObjectConfigMetadataStore(root);

            var legalHold = LegalHold.restore(true, Instant.parse("2026-07-02T09:00:00Z"));
            store.update("durable-reports", "records/a.pdf",
                config -> config.withLegalHold(legalHold));
            var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AES256);
            store.update("durable-reports", "records/a.pdf",
                config -> config.withEncryption(encryption));

            ObjectConfigMetadataStore restarted = new ObjectConfigMetadataStore(root);
            var reloaded = restarted.find("durable-reports", "records/a.pdf").orElseThrow();
            assertEquals(legalHold, reloaded.legalHold(),
                "earlier legal hold must not be lost by a later encryption update");
            assertEquals(encryption, reloaded.encryption());
        }
    }
}
