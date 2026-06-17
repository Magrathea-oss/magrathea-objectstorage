package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionContext;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainCollectionImmutabilityTest {

    @Test
    void bucketDefensivelyCopiesEventsAndExposesImmutableEvents() {
        Bucket.Id id = Bucket.Id.of("bucket-id-1");
        List<ObjectStoreEvent> events = new ArrayList<>();
        events.add(new ObjectStoreEvent.BucketCreated(id, "quality-bucket", Instant.now()));

        Bucket bucket = new Bucket(id, "quality-bucket", Region.US_EAST_1, StorageClass.STANDARD,
            false, false, false, BucketConfig.EMPTY, events);

        events.add(new ObjectStoreEvent.BucketDeleted(id, Instant.now()));

        assertEquals(1, bucket.domainEvents().size());
        assertThrows(UnsupportedOperationException.class,
            () -> bucket.domainEvents().add(new ObjectStoreEvent.BucketDeleted(id, Instant.now())));
    }

    @Test
    void multipartUploadDefensivelyCopiesPartsAndEvents() {
        MultipartUpload.Id id = MultipartUpload.Id.of("upload-record-id");
        Bucket.Id bucketId = Bucket.Id.of("bucket-id-2");
        ObjectKey key = ObjectKey.of("quality-bucket", "multipart-object.txt");
        UploadId uploadId = UploadId.of("uploadid2");
        UploadPart part = UploadPart.create(PartNumber.of(1), "\"etag-1\"", 128L);
        List<UploadPart> parts = new ArrayList<>();
        parts.add(part);
        List<ObjectStoreEvent> events = new ArrayList<>();
        events.add(new ObjectStoreEvent.MultipartUploadCreated(id, uploadId, bucketId, key, Instant.now()));

        MultipartUpload upload = new MultipartUpload(id, bucketId, key, uploadId, Instant.now(),
            parts, false, false, events);

        parts.add(UploadPart.create(PartNumber.of(2), "\"etag-2\"", 256L));
        events.add(new ObjectStoreEvent.MultipartUploadAborted(id, uploadId, Instant.now()));

        assertEquals(1, upload.parts().size());
        assertEquals(1, upload.domainEvents().size());
        assertThrows(UnsupportedOperationException.class,
            () -> upload.parts().add(UploadPart.create(PartNumber.of(3), "\"etag-3\"", 512L)));
        assertThrows(UnsupportedOperationException.class,
            () -> upload.domainEvents().add(new ObjectStoreEvent.MultipartUploadAborted(id, uploadId, Instant.now())));
    }

    @Test
    void s3ObjectDefensivelyCopiesMetadataTagsAndEvents() {
        ObjectKey key = ObjectKey.of("quality-bucket", "immutable-object.txt");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("purpose", "quality");
        var object = S3Object.create(key, "STANDARD", metadata, null, checksum(), 64L);

        metadata.put("later", "mutation");

        assertEquals(Map.of("purpose", "quality"), object.userMetadata());
        assertThrows(UnsupportedOperationException.class, () -> object.userMetadata().put("x", "y"));
        assertThrows(UnsupportedOperationException.class,
            () -> object.domainEvents().add(new ObjectStoreEvent.ObjectDeleted(key, ZonedDateTime.now())));

        Map<String, String> tags = new HashMap<>();
        tags.put("project", "magrathea");
        var tagged = object.withObjectTags(tags);
        tags.put("later", "mutation");

        assertEquals(Map.of("project", "magrathea"), tagged.objectTags());
        assertThrows(UnsupportedOperationException.class, () -> tagged.objectTags().put("x", "y"));
    }

    @Test
    void encryptionContextDefensivelyCopiesInputMap() {
        Map<String, String> context = new HashMap<>();
        context.put("tenant", "alpha");

        EncryptionContext encryptionContext = new EncryptionContext(context);
        context.put("tenant", "beta");

        assertEquals("alpha", encryptionContext.context().get("tenant"));
        assertThrows(UnsupportedOperationException.class, () -> encryptionContext.context().put("x", "y"));
    }

    @Test
    void nullOptionalMetadataIsNormalizedToEmptyMapWhereFactoriesAllowIt() {
        S3Object object = S3Object.create(ObjectKey.of("quality-bucket", "null-metadata.txt"),
            "STANDARD", null, null, checksum(), 1L);

        assertTrue(object.userMetadata().isEmpty());
    }

    private static ObjectChecksum checksum() {
        return ObjectChecksum.of(Set.of(new ChecksumValue(ChecksumAlgorithm.SHA256, "abc123")));
    }
}
