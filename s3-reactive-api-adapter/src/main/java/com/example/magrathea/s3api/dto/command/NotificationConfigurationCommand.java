package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PutBucketNotification XML body.
 */
@JacksonXmlRootElement(localName = "NotificationConfiguration")
public record NotificationConfigurationCommand(
    @JacksonXmlElementWrapper(localName = "EventConfiguration", useWrapping = false)
    @JacksonXmlProperty(localName = "EventConfiguration")
    List<EventConfiguration> eventConfigurations
) {
    public record EventConfiguration(
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
