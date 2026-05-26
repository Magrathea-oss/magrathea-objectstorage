package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketOwnershipControls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketOwnershipControls value object.
 */
class BucketOwnershipControlsTest {

    @Test
    void createValid() {
        var ctrl = new BucketOwnershipControls("test-bucket", "rule1", "BucketOwnerPreferred");
        assertEquals("test-bucket", ctrl.bucketName());
        assertEquals("BucketOwnerPreferred", ctrl.ownership());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketOwnershipControls(null, null, null));
    }
}
