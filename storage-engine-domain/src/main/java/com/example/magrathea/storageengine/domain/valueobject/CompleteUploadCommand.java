package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;
import java.util.List;

public record CompleteUploadCommand(
        UploadRequestContext context,
        UploadMode uploadMode,
        Optional<List<PartDescriptor>> parts) {

    public CompleteUploadCommand {
        java.util.Objects.requireNonNull(context, "context must not be null");
        java.util.Objects.requireNonNull(uploadMode, "uploadMode must not be null");
        java.util.Objects.requireNonNull(parts, "parts must not be null");
    }
}
