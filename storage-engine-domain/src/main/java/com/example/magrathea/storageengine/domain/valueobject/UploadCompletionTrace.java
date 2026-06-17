package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;
import java.util.List;

public record UploadCompletionTrace(
        UploadMode uploadMode,
        Optional<DeclaredChecksum> declaredChecksum,
        ContentHash consolidatedChecksum,
        boolean verificationPassed,
        long totalObjectSize,
        boolean metadataValidated,
        Optional<List<PartChecksumResult>> partChecksumResults) {

    public UploadCompletionTrace {
        java.util.Objects.requireNonNull(uploadMode, "uploadMode must not be null");
        java.util.Objects.requireNonNull(declaredChecksum, "declaredChecksum must not be null");
        java.util.Objects.requireNonNull(consolidatedChecksum, "consolidatedChecksum must not be null");
        if (totalObjectSize < 0) {
            throw new IllegalArgumentException("totalObjectSize must be >= 0: " + totalObjectSize);
        }
        java.util.Objects.requireNonNull(partChecksumResults, "partChecksumResults must not be null");
        partChecksumResults = partChecksumResults.map(List::copyOf);
    }
}
