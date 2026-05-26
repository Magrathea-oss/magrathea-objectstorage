package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketNotificationConfiguration.NotificationEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketNotificationConfiguration value object.
 */
class BucketNotificationConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketNotificationConfiguration("test-bucket", null);
        assertEquals("test-bucket", config.bucketName());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketNotificationConfiguration(null, null));
    }

    @Test
    void withEvents() {
        var event = new NotificationEvent("s3:ObjectCreated:*", "topic-arn", null, null, null);
        var config = new BucketNotificationConfiguration("bucket", List.of(event));
        assertEquals(1, config.events().size());
        assertEquals("s3:ObjectCreated:*", config.events().getFirst().event());
    }
}
