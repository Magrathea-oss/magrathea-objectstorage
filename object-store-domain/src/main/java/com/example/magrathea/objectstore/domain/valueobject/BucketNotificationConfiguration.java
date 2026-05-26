package com.example.magrathea.objectstore.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * AWS S3 bucket notification configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketNotificationConfiguration(
    String bucketName,
    List<NotificationEvent> events
) {
    public BucketNotificationConfiguration {
        Objects.requireNonNull(bucketName);
        if (events != null) {
            events = List.copyOf(events);
        }
    }

    public record NotificationEvent(
        String event,
        String topicArn,
        String queueArn,
        String lambdaArn,
        List<String> filterRules
    ) {
        public NotificationEvent {
            Objects.requireNonNull(event);
        }
    }

    public boolean hasNotification() {
        return events != null && !events.isEmpty();
    }
}
