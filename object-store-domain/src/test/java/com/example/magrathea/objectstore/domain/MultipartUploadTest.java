package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultipartUploadTest {

    @Test
    void shouldCreateMultipartUpload() {
        MultipartUpload.Id id = MultipartUpload.Id.generate();
        Bucket.Id bucketId = Bucket.Id.generate();
        ObjectKey key = ObjectKey.of("test-bucket", "test-key.txt");
        UploadId uploadId = UploadId.generate();
        MultipartUpload upload = MultipartUpload.create(id, bucketId, key, uploadId);

        assertEquals(id, upload.id());
        assertEquals(bucketId, upload.bucketId());
        assertEquals(key, upload.key());
        assertEquals(uploadId, upload.uploadId());
        assertTrue(upload.isActive());
        assertFalse(upload.hasParts());
        assertEquals(0, upload.partCount());
    }

    @Test
    void shouldAddPartToUpload() {
        MultipartUpload upload = createActiveUpload();
        UploadPart part = UploadPart.create(PartNumber.of(1), "\"etag1\"", 512);

        MultipartUpload updated = upload.withPart(part);
        assertEquals(1, updated.partCount());
        assertTrue(updated.hasParts());
        assertEquals("\"etag1\"", updated.parts().getFirst().etag());
    }

    @Test
    void shouldCompleteUpload() {
        MultipartUpload upload = createActiveUpload();
        MultipartUpload completed = upload.withPart(UploadPart.create(PartNumber.of(1), "\"etag1\"", 512))
            .withCompleted();

        assertTrue(completed.completed());
        assertFalse(completed.isActive());
    }

    @Test
    void shouldAbortUpload() {
        MultipartUpload upload = createActiveUpload();
        MultipartUpload aborted = upload.withAborted();

        assertTrue(aborted.aborted());
        assertFalse(aborted.isActive());
    }

    @Test
    void shouldRejectAddPartToCompletedUpload() {
        MultipartUpload upload = createActiveUpload().withCompleted();
        assertThrows(IllegalStateException.class,
            () -> upload.withPart(UploadPart.create(PartNumber.of(1), "\"etag\"", 512)));
    }

    @Test
    void shouldRejectAddPartToAbortedUpload() {
        MultipartUpload upload = createActiveUpload().withAborted();
        assertThrows(IllegalStateException.class,
            () -> upload.withPart(UploadPart.create(PartNumber.of(1), "\"etag\"", 512)));
    }

    @Test
    void shouldRejectCompleteAbortedUpload() {
        MultipartUpload upload = createActiveUpload().withAborted();
        assertThrows(IllegalStateException.class, upload::withCompleted);
    }

    @Test
    void shouldRejectAbortCompletedUpload() {
        MultipartUpload upload = createActiveUpload().withCompleted();
        assertThrows(IllegalStateException.class, upload::withAborted);
    }

    @Test
    void shouldRestoreFromPersistence() {
        MultipartUpload.Id id = MultipartUpload.Id.generate();
        Bucket.Id bucketId = Bucket.Id.generate();
        ObjectKey key = ObjectKey.of("test-bucket", "key");
        UploadId uploadId = UploadId.generate();
        UploadPart part = UploadPart.create(PartNumber.of(1), "\"etag\"", 100);

        MultipartUpload restored = MultipartUpload.restore(id, bucketId, key, uploadId,
            java.time.Instant.now(), java.util.List.of(part), false, false);

        assertEquals(1, restored.partCount());
        assertTrue(restored.isActive());
    }

    private MultipartUpload createActiveUpload() {
        return MultipartUpload.create(
            MultipartUpload.Id.generate(),
            Bucket.Id.generate(),
            ObjectKey.of("test-bucket", "test-key.txt"),
            UploadId.generate()
        );
    }
}
