package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for ObjectKey value object.
 */
class ObjectKeyTest {

    @Test
    void of_validKey() {
        var key = ObjectKey.of("my-file.txt");
        assertEquals("my-file.txt", key.value());
    }

    @Test
    void of_null_throws() {
        assertThrows(NullPointerException.class, () -> ObjectKey.of(null));
    }

    @Test
    void of_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of(""));
    }

    @Test
    void of_whitespaceKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKey.of("   "));
    }

    @Test
    void of_keyWithPath() {
        var key = ObjectKey.of("subdir/file.txt");
        assertEquals("subdir/file.txt", key.value());
    }

    @Test
    void equality() {
        var k1 = ObjectKey.of("test");
        var k2 = ObjectKey.of("test");
        var k3 = ObjectKey.of("other");
        assertEquals(k1, k2);
        assertNotEquals(k1, k3);
    }
}
