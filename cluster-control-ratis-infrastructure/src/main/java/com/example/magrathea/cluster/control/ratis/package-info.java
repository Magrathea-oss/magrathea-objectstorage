/**
 * Apache Ratis 3.2.2 control-plane infrastructure for the fixed EP-10 A/B/C test group.
 *
 * <p>Owns atomic identity persistence, server lifecycle, deterministic commands and snapshots,
 * leader-mediated queries, and bounded-elastic isolation of blocking Ratis APIs. It intentionally does
 * not provide dynamic membership, data transport, mTLS, healing, rebalance, or S3 integration.
 */
package com.example.magrathea.cluster.control.ratis;
