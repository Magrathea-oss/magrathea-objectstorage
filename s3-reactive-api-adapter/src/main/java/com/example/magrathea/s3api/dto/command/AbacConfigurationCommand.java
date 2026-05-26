package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PutBucketAbac XML body — Jackson XML annotated for codec deserialization.
 *
 * <pre>{@code
 * <AbacConfiguration>
 *   <AbacRule>
 *     <Id>rule-id</Id>
 *     <Principal>arn:aws:iam::...:user/...</Principal>
 *     <Resource>arn:aws:s3:::bucket/*</Resource>
 *     <Action>s3:GetObject</Action>
 *     <Condition>
 *       <Tag>department</Tag>
 *       <Value>engineering</Value>
 *     </Condition>
 *   </AbacRule>
 * </AbacConfiguration>
 * }</pre>
 */
@JacksonXmlRootElement(localName = "AbacConfiguration")
public record AbacConfigurationCommand(
    @JacksonXmlElementWrapper(localName = "AbacRule", useWrapping = false)
    @JacksonXmlProperty(localName = "AbacRule")
    List<AbacRuleDto> rules
) {
    public record AbacRuleDto(
        @JacksonXmlProperty(localName = "Id")
        String id,
        @JacksonXmlProperty(localName = "Principal")
        String principal,
        @JacksonXmlProperty(localName = "Resource")
        String resource,
        @JacksonXmlProperty(localName = "Action")
        String action,
        @JacksonXmlElementWrapper(localName = "Condition", useWrapping = false)
        @JacksonXmlProperty(localName = "Condition")
        List<ConditionDto> conditions
    ) {}

    public record ConditionDto(
        @JacksonXmlProperty(localName = "Tag")
        String tag,
        @JacksonXmlProperty(localName = "Value")
        String value
    ) {}
}
