/**
 * Transport-neutral EP-10 cluster application boundary for REQ-CLUSTER-008 through REQ-CLUSTER-010
 * and consensus-owned repair control in REQ-CLUSTER-021 through REQ-CLUSTER-026.
 *
 * <p>Defines fixed membership and control/data endpoints, stable identity and failure-domain identity,
 * bucket/reference generations, prepared immutable artifacts, durable acknowledgement evidence, PA-6
 * placement coordination, fenced compare-and-publish orchestration, reactive control/data ports, and
 * transport-neutral repair coordination, worker fencing, and bounded consensus-state scheduling.
 * Filesystem, network transport, and serialization types remain outside this package.
 */
package com.example.magrathea.storageengine.cluster.application;
