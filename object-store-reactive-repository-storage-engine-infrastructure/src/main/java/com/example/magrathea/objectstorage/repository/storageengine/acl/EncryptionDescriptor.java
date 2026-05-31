package com.example.magrathea.objectstorage.repository.storageengine.acl;

import com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionContext;

/**
 * ACL helper — translates Object Store encryption concepts to Storage Engine encryption concepts.
 * <p>
 * An Object Store {@code EncryptionConfiguration} consists of an algorithm, key reference,
 * and context. The Storage Engine equivalent is {@code EncryptionRequest} (mode + key reference).
 * </p>
 */
public record EncryptionDescriptor(
    EncryptionAlgorithm algorithm,
    EncryptionKeyReference keyReference,
    EncryptionContext encryptionContext
) {
    public static EncryptionDescriptor of(
            EncryptionAlgorithm algorithm,
            EncryptionKeyReference keyReference,
            EncryptionContext encryptionContext) {
        return new EncryptionDescriptor(algorithm, keyReference, encryptionContext);
    }
}
