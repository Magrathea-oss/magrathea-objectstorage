package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for Region value object.
 */
class RegionTest {

    @Test
    void predefinedRegions() {
        assertEquals("us-east-1", Region.US_EAST_1.id());
        assertEquals("US East (N. Virginia)", Region.US_EAST_1.name());
        assertTrue(Region.US_EAST_1.supported());

        assertEquals("eu-west-1", Region.EU_WEST_1.id());
        assertEquals("eu-central-1", Region.EU_CENTRAL_1.id());
        assertEquals("us-west-2", Region.US_WEST_2.id());
    }

    @Test
    void customRegion() {
        var r = new Region("ap-southeast-1", "Asia Pacific (Singapore)", true);
        assertEquals("ap-southeast-1", r.id());
        assertTrue(r.supported());
    }

    @Test
    void nullId_throws() {
        assertThrows(NullPointerException.class,
            () -> new Region(null, "test", true));
    }

    @Test
    void nullName_throws() {
        assertThrows(NullPointerException.class,
            () -> new Region("test", null, true));
    }
}
