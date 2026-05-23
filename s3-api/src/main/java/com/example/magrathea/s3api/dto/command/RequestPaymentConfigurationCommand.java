package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketRequestPayment XML body.
 */
@JacksonXmlRootElement(localName = "RequestPaymentConfiguration")
public record RequestPaymentConfigurationCommand(
    @JacksonXmlProperty(localName = "Payer")
    String payer
) {}
