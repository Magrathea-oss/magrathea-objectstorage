package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.valueobject.BucketNotificationConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;
import java.util.Optional;

/**
 * Response for GET /{bucket}?notification (GetBucketNotification).
 */
@JacksonXmlRootElement(localName = "NotificationConfiguration")
public record BucketNotificationQuery(
    @JacksonXmlProperty(localName = "EventConfiguration")
    List<EventConfigurationEntry> eventConfigurations
) {
    public static BucketNotificationQuery from(Optional<BucketNotificationConfiguration> config) {
        var events = config.map(c -> c.events().stream()
            .map(e -> new EventConfigurationEntry(
                e.event(), e.topicArn(), e.queueArn(), e.lambdaArn(), e.filterRules()))
            .toList()).orElse(List.of());
        return new BucketNotificationQuery(events);
    }

    public record EventConfigurationEntry(
        @JacksonXmlProperty(localName = "Event")
        String event,
        @JacksonXmlProperty(localName = "TopicARN")
        String topicArn,
        @JacksonXmlProperty(localName = "QueueARN")
        String queueArn,
        @JacksonXmlProperty(localName = "LambdaARN")
        String lambdaArn,
        @JacksonXmlElementWrapper(localName = "FilterRule", useWrapping = false)
        @JacksonXmlProperty(localName = "FilterRule")
        List<String> filterRules
    ) {}
}
