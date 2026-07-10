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
