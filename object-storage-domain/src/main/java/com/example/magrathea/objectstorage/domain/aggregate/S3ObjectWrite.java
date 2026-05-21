package com.example.magrathea.objectstorage.domain.aggregate;

/**
 * Domain-level interface for an object write request.
 *
 * The domain contract exposes only S3 object metadata. Infrastructure-specific
 * implementations may carry the actual content stream without leaking framework
 * types into the domain module.
 */
public interface S3ObjectWrite {
    S3Object s3Object();
}
