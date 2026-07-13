/**
 * Internal grpc-java direct replica-data infrastructure boundary.
 *
 * <p>The adapter transfers immutable artifact identifiers and bytes only. Netty-shaded gRPC,
 * mutual TLS identity checks, manual flow control, durable staging, and cleanup remain isolated
 * here; no S3 bucket, key, policy, or publication decision crosses this boundary.
 */
package com.example.magrathea.cluster.data.grpc;
