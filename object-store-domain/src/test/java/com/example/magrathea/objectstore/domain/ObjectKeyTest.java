package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for ObjectKey value object (bucket + key composite).
 */
class ObjectKeyTest {

    private static final String BUCKET = "my-bucket";
    private static final String KEY = "my-file.txt";

    @Test
    void of_validBucketAndKey() {
        var key = ObjectKey.of(BUCKET, KEY);
        assertEquals(BUCKET, key.bucket());
        assertEquals(KEY, key.key());
    }

    @Test
    void of_nullBucket_throws() {
        assertThrows(NullPointerException.class, () -> ObjectKey.of(null, KEY));
    }

    @Test
    void of_nullKey_throws() {
        assertThrows(NullPointerException.class, () -> ObjectKey.of(BUCKET, null));
    }

    @Test
    void of_blankBucket_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of("", KEY));
    }

    @Test
    void of_whitespaceBucket_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of("   ", KEY));
    }

    @Test
    void of_blankKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of(BUCKET, ""));
    }

    @Test
    void of_whitespaceKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of(BUCKET, "   "));
    }

    @Test
    void of_keyExceeds1024_throws() {
        var longKey = "a".repeat(1025);
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of(BUCKET, longKey));
    }

    @Test
    void of_keyWithPath() {
        var key = ObjectKey.of(BUCKET, "subdir/file.txt");
        assertEquals("subdir/file.txt", key.key());
        assertEquals(BUCKET, key.bucket());
    }

    @Test
    void toString_usesBucketSlashKey() {
        var key = ObjectKey.of("bucket-a", "path/to/object");
        assertEquals("bucket-a/path/to/object", key.toString());
    }

    @Test
    void equality() {
        var k1 = ObjectKey.of(BUCKET, KEY);
        var k2 = ObjectKey.of(BUCKET, KEY);
        var k3 = ObjectKey.of(BUCKET, "other.txt");
        var k4 = ObjectKey.of("other-bucket", KEY);
        assertEquals(k1, k2);
        assertNotEquals(k1, k3);
        assertNotEquals(k1, k4);
    }
}
