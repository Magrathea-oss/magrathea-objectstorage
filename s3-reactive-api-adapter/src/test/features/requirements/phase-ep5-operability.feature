@requirement @phase-ep5 @operability @admin-api @business-need
Business Need: EP-5 operational health probes
  Operators need explicit Admin API liveness and readiness probes
  so container platforms and CI smoke gates can distinguish a running process from a deployment ready to serve configured storage operations.

  Rule: Admin API probes expose process liveness and catalog readiness without using S3 object routes

    @implemented-and-validated @REQ-OPS-001 @functional-requirement @observability @liveness
    Scenario: Admin liveness reports that the process is running
      Given the Admin API is configured with storage policy, storage device, and disk-set catalogs
      When an Admin API client requests GET "/admin/live"
      Then the Admin API response status is 200
      And the Admin API response field "probe" is "liveness"
      And the Admin API response field "status" is "ok"
      And the Admin API response has a link named "ready" to "/admin/ready"

    @implemented-and-validated @REQ-OPS-002 @functional-requirement @observability @readiness @storage-policy @storage-device @disk-set
    Scenario: Admin readiness reports configured storage catalogs before accepting traffic
      Given the Admin API is configured with storage policy, storage device, and disk-set catalogs
      When an Admin API client requests GET "/admin/ready"
      Then the Admin API response status is 200
      And the Admin API response field "probe" is "readiness"
      And the Admin API response field "status" is "ready"
      And the Admin API readiness components are ready:
        | component              |
        | storage-policy-catalog |
        | storage-device-catalog |
        | disk-set-catalog       |
      And the Admin API response has a link named "live" to "/admin/live"

    @implemented-and-validated @REQ-OPS-003 @functional-requirement @observability @readiness @fail-closed
    Scenario: Admin readiness fails closed when required storage catalogs are unavailable
      Given the Admin API is missing storage policy, storage device, and disk-set catalogs
      When an Admin API client requests GET "/admin/ready"
      Then the Admin API response status is 503
      And the Admin API response field "probe" is "readiness"
      And the Admin API response field "status" is "not-ready"
      And the Admin API readiness components have status:
        | component              | status         |
        | storage-policy-catalog | not-configured |
        | storage-device-catalog | not-configured |
        | disk-set-catalog       | not-configured |
      And the Admin API response has a link named "live" to "/admin/live"

  Rule: Graceful shutdown protects committed storage-engine state

    @implemented-and-validated @REQ-OPS-004 @functional-requirement @non-functional-requirement @observability @graceful-shutdown @restart-safety @durability
    Scenario: Storage-engine process terminates on SIGTERM without losing committed S3 objects
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-graceful-shutdown/current"
      And bucket "ep5-graceful-shutdown-bucket" contains object "objects/shutdown-drain.txt" with body "committed before shutdown"
      When operators send SIGTERM to the S3 process
      Then the process exits within 10 seconds without forced termination
      And the shutdown log must not contain Spring Boot's generated security password banner
      When recovery starts the S3 process again with the same filesystem root
      Then object "objects/shutdown-drain.txt" in bucket "ep5-graceful-shutdown-bucket" can be read with body "committed before shutdown"

    @implemented-and-validated @REQ-OPS-009 @functional-requirement @non-functional-requirement @observability @graceful-shutdown @request-draining @streaming @durability
    Scenario: SIGTERM drains an in-flight streaming PutObject before process exit
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-graceful-drain/current"
      And bucket "ep5-graceful-drain-bucket" is created before in-flight shutdown validation
      When an S3 client starts streaming 524288 deterministic bytes to object "objects/in-flight.bin" in bucket "ep5-graceful-drain-bucket"
      And operators send SIGTERM after request body delivery has started
      Then the in-flight PutObject completes with HTTP status 200
      And the process exits within 10 seconds without forced termination
      And the shutdown log must not contain Spring Boot's generated security password banner
      When recovery starts the S3 process again with the same filesystem root
      Then object "objects/in-flight.bin" in bucket "ep5-graceful-drain-bucket" has 524288 bytes with the streamed content checksum

    @implemented-and-validated @REQ-OPS-010 @functional-requirement @non-functional-requirement @observability @graceful-shutdown @request-draining @streaming @multipart @durability @restart-safety
    Scenario: SIGTERM drains an in-flight multipart UploadPart that remains completable after restart
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-graceful-multipart-drain/current"
      And bucket "ep5-graceful-multipart-drain-bucket" is created before in-flight shutdown validation
      And a multipart upload is initiated for object "objects/in-flight-multipart.bin" in bucket "ep5-graceful-multipart-drain-bucket"
      When an S3 client starts streaming 524288 deterministic bytes as part 1 of the recorded multipart upload
      And operators send SIGTERM after request body delivery has started
      Then the in-flight UploadPart completes with HTTP status 200 and records its ETag
      And the process exits within 10 seconds without forced termination
      And the shutdown log must not contain Spring Boot's generated security password banner
      When recovery starts the S3 process again with the same filesystem root
      And operators complete the recorded multipart upload using the drained part
      Then object "objects/in-flight-multipart.bin" in bucket "ep5-graceful-multipart-drain-bucket" has 524288 bytes with the streamed content checksum

    @implemented-and-validated @REQ-OPS-011 @functional-requirement @non-functional-requirement @observability @graceful-shutdown @request-draining @streaming @concurrency @durability @restart-safety
    Scenario: SIGTERM drains concurrent streaming PutObject requests before process exit
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-graceful-concurrent-drain/current"
      And bucket "ep5-graceful-concurrent-drain-bucket" is created before in-flight shutdown validation
      When S3 clients concurrently stream these deterministic objects to bucket "ep5-graceful-concurrent-drain-bucket":
        | object key                 | bytes  |
        | objects/concurrent-a.bin   | 262144 |
        | objects/concurrent-b.bin   | 262144 |
      And operators send SIGTERM after every concurrent request body has started
      Then every concurrent PutObject completes with HTTP status 200
      And the process exits within 10 seconds without forced termination
      And the shutdown log must not contain Spring Boot's generated security password banner
      When recovery starts the S3 process again with the same filesystem root
      Then the concurrently drained objects in bucket "ep5-graceful-concurrent-drain-bucket" retain their streamed content:
        | object key                 | bytes  |
        | objects/concurrent-a.bin   | 262144 |
        | objects/concurrent-b.bin   | 262144 |

    @implemented-and-validated @REQ-OPS-012 @functional-requirement @non-functional-requirement @observability @graceful-shutdown @request-draining @multipart @durability @restart-safety
    Scenario: SIGTERM drains an in-flight CompleteMultipartUpload before process exit
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-graceful-multipart-completion/current"
      And bucket "ep5-graceful-multipart-completion-bucket" is created before in-flight shutdown validation
      And a multipart upload is initiated for object "objects/completing-at-shutdown.bin" in bucket "ep5-graceful-multipart-completion-bucket"
      And part 1 of the recorded multipart upload contains 524288 deterministic bytes
      When an S3 client starts completing the recorded multipart upload with a throttled XML request body
      And operators send SIGTERM after multipart completion body delivery has started
      Then the in-flight CompleteMultipartUpload completes with HTTP status 200
      And the process exits within 10 seconds without forced termination
      And the shutdown log must not contain Spring Boot's generated security password banner
      When recovery starts the S3 process again with the same filesystem root
      Then object "objects/completing-at-shutdown.bin" in bucket "ep5-graceful-multipart-completion-bucket" has 524288 bytes with the streamed content checksum

    @implemented-and-validated @REQ-OPS-013 @functional-requirement @non-functional-requirement @observability @graceful-shutdown @multipart @cancellation @cleanup @durability @restart-safety
    Scenario: Client cancellation and multipart abort before SIGTERM leave no committed object or part artifacts
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-graceful-multipart-cancellation/current"
      And bucket "ep5-graceful-multipart-cancellation-bucket" is created before in-flight shutdown validation
      And a multipart upload is initiated for object "objects/cancelled-at-shutdown.bin" in bucket "ep5-graceful-multipart-cancellation-bucket"
      When an S3 client starts streaming 524288 deterministic bytes as part 1 of the recorded multipart upload
      And the client cancels the in-flight UploadPart after body delivery starts
      And operators abort the recorded multipart upload before sending SIGTERM
      And operators send SIGTERM to the S3 process
      Then the process exits within 10 seconds without forced termination
      And the shutdown log must not contain Spring Boot's generated security password banner
      When recovery starts the S3 process again with the same filesystem root
      Then the cancelled multipart upload has no committed object, active upload, or part artifacts

  Rule: Backup and restore rehearsals prove recoverability from storage-engine filesystem backups

    @implemented-and-validated @REQ-OPS-005 @functional-requirement @non-functional-requirement @backup @restore @disaster-recovery @durability @restart-safety
    Scenario: Storage-engine filesystem backup restores committed S3 data after primary data loss
      Given a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-backup-restore/current"
      And bucket "ep5-backup-restore-bucket" contains object "objects/restored.txt" with body "restored from backup"
      When operators stop the S3 process for a backup window
      And operators copy the storage-engine filesystem root to backup location "target/ep5-backup-restore/backup"
      And the primary storage-engine filesystem root is lost
      And operators restore the backup to the primary filesystem root
      And recovery starts the S3 process again with the same filesystem root
      Then object "objects/restored.txt" in bucket "ep5-backup-restore-bucket" can be read with body "restored from backup"

    @implemented-and-validated @REQ-OPS-006 @functional-requirement @non-functional-requirement @disaster-recovery @rto @rpo @durability @restart-safety
    Scenario: Single-node disaster recovery rehearsal meets the declared offline RTO and RPO
      Given the single-node disaster recovery objective declares RTO 30 seconds and RPO "last completed offline backup"
      And a storage-engine S3 process is running with graceful shutdown enabled and filesystem root "target/ep5-disaster-recovery/current"
      And bucket "ep5-disaster-recovery-bucket" contains object "objects/rpo.txt" with body "inside the recovery point"
      When operators stop the S3 process for a backup window
      And operators copy the storage-engine filesystem root to backup location "target/ep5-disaster-recovery/backup"
      And the primary storage-engine filesystem root is lost
      And operators start disaster recovery from the backup location
      Then disaster recovery completes within the declared RTO
      And object "objects/rpo.txt" in bucket "ep5-disaster-recovery-bucket" can be read with body "inside the recovery point"
      And the recovered data satisfies the declared RPO

  Rule: Shipped SLO and alert rules give operators actionable first-response guidance

    @implemented-not-e2e-validated @REQ-OPS-008 @functional-requirement @non-functional-requirement @slo @alerting @prometheus @loki @runbook
    Scenario: Packaged alerting bundle declares SLOs and first-response alerts for single-node operations
      Given the EP-5 SLO runbook exists at "docs/runbooks/slo-alerts.md"
      And the Prometheus alert rule pack exists at "ops/prometheus/magrathea-objectstorage-alerts.yml"
      And the Loki alert rule pack exists at "ops/loki/magrathea-objectstorage-log-alerts.yml"
      When operators inspect the shipped EP-5 alerting bundle
      Then the SLO runbook declares objectives:
        | objective                  | target | signal                                                 |
        | admin-liveness            | 99.9%  | GET /admin/live                                       |
        | admin-readiness           | 99.5%  | GET /admin/ready                                      |
        | s3-smoke-availability     | 99.0%  | S3 ListBuckets/PutObject/GetObject smoke probe        |
        | backup-rpo                | 24h    | last completed offline backup                         |
        | security-mode-safety      | 0      | Spring Boot generated-password banner                 |
        | schema-migration-safety   | 0      | Unsupported manifest schema version                   |
      And the Prometheus rule pack declares alerts:
        | alert                                      | severity | expression fragment                                      |
        | MagratheaAdminLivenessProbeDown            | page     | magrathea-admin-live                                     |
        | MagratheaAdminReadinessProbeNotReady       | page     | magrathea-admin-ready                                    |
        | MagratheaS3SmokeProbeFailing               | page     | magrathea-s3-smoke                                       |
        | MagratheaStorageEngineFilesystemNearFull   | ticket   | storage_filesystem_available_ratio                       |
        | MagratheaOfflineBackupTooOld               | ticket   | magrathea_last_successful_offline_backup_timestamp_seconds |
      And the Loki rule pack declares log alerts:
        | alert                                             | severity | query fragment                         |
        | MagratheaGeneratedSecurityPasswordBannerDetected  | page     | generated security password            |
        | MagratheaUnsupportedManifestSchemaDetected        | ticket   | Unsupported manifest schema version    |
      And every shipped alert includes a runbook link
      And the generated-password log alert searches for the Spring Boot generated-password banner
