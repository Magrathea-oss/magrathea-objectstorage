package com.example.magrathea.objectstorage.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Input command for creating a bucket — Java 17+ record.
 */
public record CreateBucketCommand(
    @NotBlank
    @Size(min = 3, max = 63)
    @Pattern(regexp = "^[a-z0-9][a-z0-9.-]*[a-z0-9]$")
    String name,

    @NotBlank
    String region,

    @NotBlank
    String storageClass
) {}
