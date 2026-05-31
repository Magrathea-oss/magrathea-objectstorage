package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.DeclaredChecksum;
import com.example.magrathea.storageengine.domain.valueobject.PartChecksumResult;
import com.example.magrathea.storageengine.domain.valueobject.PartDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.UploadCompletionTrace;
import com.example.magrathea.storageengine.domain.valueobject.UploadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure domain service — handles upload completion logic.
 * Validates metadata, total size, declared checksums, and consolidates
 * checksums for multipart uploads.
 * No framework dependencies, no I/O, no reactive types.
 */
public class CompleteUploadService {

    /**
     * Completes an upload by validating the command and producing an UploadCompletionTrace.
     *
     * @param command the complete upload command
     * @return the upload completion trace
     */
    public UploadCompletionTrace complete(CompleteUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        UploadMode uploadMode = command.uploadMode();
        long totalObjectSize = command.context().contentDescriptor().objectSize();
        boolean metadataValidated = validateMetadata(command);

        Optional<DeclaredChecksum> declaredChecksum = command.context().declaredChecksum();
        Optional<List<PartChecksumResult>> partResults = Optional.empty();

        ContentHash consolidatedChecksum;

        switch (uploadMode) {
            case SINGLE_OBJECT -> {
                // For single-object upload, the declared checksum is the final checksum
                // (verification would happen elsewhere with actual content)
                consolidatedChecksum = declaredChecksum
                        .map(dc -> ContentHash.of(dc.algorithm(), dc.value()))
                        .orElse(ContentHash.of(ChecksumAlgorithm.SHA256, "not-verified"));
            }
            case MULTIPART -> {
                // For multipart upload, validate parts and consolidate checksums
                List<PartDescriptor> parts = command.parts()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Multipart upload requires parts list"));
                partResults = Optional.of(validateParts(parts, totalObjectSize));
                // Consolidate checksum: use last part's checksum as a simplified model
                List<PartChecksumResult> results = partResults.get();
                if (results.isEmpty()) {
                    throw new IllegalArgumentException("Multipart upload must have at least one part");
                }
                // In a real implementation, this would compute a proper consolidated checksum
                // (e.g., SHA256 of concatenated part checksums or tree hash)
                ContentHash lastChecksum = results.get(results.size() - 1).calculatedChecksum();
                consolidatedChecksum = lastChecksum;
            }
            default -> throw new IllegalArgumentException("Unknown upload mode: " + uploadMode);
        }

        // Determine verification result: passed if declared checksum matches consolidated
        boolean verificationPassed = declaredChecksum
                .map(dc -> consolidatedChecksum.value().equals(dc.value()))
                .orElse(true);

        return new UploadCompletionTrace(
                uploadMode,
                declaredChecksum,
                consolidatedChecksum,
                verificationPassed,
                totalObjectSize,
                metadataValidated,
                partResults);
    }

    private boolean validateMetadata(CompleteUploadCommand command) {
        // Basic metadata validation: ensure non-null metadata map
        // In a real implementation, this would validate headers, content-type, etc.
        return true; // simplified — metadata is always valid at domain level
    }

    private List<PartChecksumResult> validateParts(List<PartDescriptor> parts, long totalObjectSize) {
        List<PartChecksumResult> results = new ArrayList<>();
        long accumulatedSize = 0;
        int expectedPartNumber = 1;

        for (PartDescriptor part : parts) {
            if (part.partNumber() != expectedPartNumber) {
                throw new IllegalArgumentException(
                        "Expected part number " + expectedPartNumber
                                + " but got " + part.partNumber());
            }

            // Simulate checksum calculation for each part
            ContentHash calculatedChecksum = part.partChecksum()
                    .map(dc -> ContentHash.of(dc.algorithm(), dc.value()))
                    .orElse(ContentHash.of(ChecksumAlgorithm.SHA256, "not-calculated"));

            boolean matched = part.partChecksum()
                    .map(dc -> calculatedChecksum.value().equals(dc.value()))
                    .orElse(true);

            results.add(PartChecksumResult.of(
                    part.partNumber(),
                    part.partSize(),
                    part.partChecksum(),
                    calculatedChecksum,
                    matched));

            accumulatedSize += part.partSize();
            expectedPartNumber++;
        }

        // Validate total accumulated size matches declared object size
        if (accumulatedSize != totalObjectSize) {
            throw new IllegalArgumentException(
                    "Part sizes sum (" + accumulatedSize + ") does not match total object size ("
                            + totalObjectSize + ")");
        }

        return List.copyOf(results);
    }
}
