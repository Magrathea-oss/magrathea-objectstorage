package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?abac (GetBucketAbac).
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
public record AbacConfigurationQuery(
    @JacksonXmlElementWrapper(localName = "AbacRule", useWrapping = false)
    @JacksonXmlProperty(localName = "AbacRule")
    List<AbacRuleEntry> rules
) {
    public record AbacRuleEntry(
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
        List<ConditionEntry> conditions
    ) {}

    public record ConditionEntry(
        @JacksonXmlProperty(localName = "Tag")
        String tag,
        @JacksonXmlProperty(localName = "Value")
        String value
    ) {}
}
