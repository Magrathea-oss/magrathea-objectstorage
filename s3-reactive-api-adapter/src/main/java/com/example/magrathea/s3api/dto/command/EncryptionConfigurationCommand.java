package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketEncryptionConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "ServerSideEncryptionConfiguration")
public record EncryptionConfigurationCommand(
    @JacksonXmlProperty(localName = "RuleID")
    String ruleId,
    @JacksonXmlProperty(localName = "Algorithm")
    String algorithm,
    @JacksonXmlProperty(localName = "KMSKeyId")
    String kmsKeyId
) {}
