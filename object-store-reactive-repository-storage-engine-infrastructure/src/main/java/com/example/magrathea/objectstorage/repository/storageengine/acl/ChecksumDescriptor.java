package com.example.magrathea.objectstorage.repository.storageengine.acl;

import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;

/**
 * ACL helper — translates Object Store checksum concepts to Storage Engine checksum concepts.
 * <p>
 * An Object Store checksum consists of a typed algorithm + BASE64-encoded value.
 * The Storage Engine equivalent is a {@code DeclaredChecksum} (algorithm enum + string value).
 * </p>
 */
public record ChecksumDescriptor(
    ChecksumAlgorithm algorithm,
    String base64Value
) {
    public static ChecksumDescriptor of(ChecksumAlgorithm algorithm, String base64Value) {
        return new ChecksumDescriptor(algorithm, base64Value);
    }

    public static ChecksumDescriptor fromChecksumValue(ChecksumValue value) {
        return new ChecksumDescriptor(value.algorithm(), value.base64Value());
    }
}
