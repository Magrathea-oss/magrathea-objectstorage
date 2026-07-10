@requirement @phase-ep2 @metadata-durability @storage-engine
Business Need: EP-2 Complete Metadata Durability
  All S3-visible metadata families — the bucket registry, multipart upload state,
  per-object configuration, and bucket configuration families — must survive a full
  process stop and start when the storage-engine backend is active. The in-memory
  (single-node) profile is explicitly exempt: it does not claim restart durability.

  Durable state lives under the storage-engine filesystem root:
  bucket registry documents under "metadata/buckets", multipart upload sessions under
  "metadata/multipart-uploads", and per-object configuration documents under
  "metadata/object-config". Every commit is crash-safe (temp file + atomic rename).

  Rule: The bucket registry must survive restart in storage-engine mode
    Buckets created through the S3 API, including their full configuration document,
    are reloaded from the filesystem when the process restarts.

    @REQ-DUR-001 @functional-requirement @non-functional-requirement @durability @restart-safety @bootstrap-integration-required @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket registry survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-001-bucket-registry"
      And bucket "ep2-bucket-registry-test" is created through the S3 CreateBucket API
      When the application process is stopped and started again with the same filesystem root
      Then a HeadBucket request for bucket "ep2-bucket-registry-test" returns HTTP 200
      And the ListBuckets response includes bucket "ep2-bucket-registry-test"
      And re-creating bucket "ep2-bucket-registry-test" is rejected because the bucket already exists

    @REQ-DUR-001 @functional-requirement @in-memory-exemption @implemented-not-e2e-validated
    Scenario: In-memory single-node mode is exempt from bucket registry durability
      Given the in-memory single-node profile is active
      And bucket "ep2-memory-exempt-test" is created through the S3 CreateBucket API
      When the application process is stopped and started again
      Then a HeadBucket request for bucket "ep2-memory-exempt-test" returns HTTP 404
      And the ListBuckets response does not include bucket "ep2-memory-exempt-test"

  Rule: Multipart upload state must survive restart in storage-engine mode
    The upload id, bucket, key, initiated timestamp and every recorded part
    (part number, ETag, size) are reloaded from the filesystem after a restart,
    so in-progress uploads can be listed and aborted after recovery.

    @REQ-DUR-002 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Multipart upload state survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-002-multipart-state"
      And a multipart upload is initiated for bucket "ep2-mpu-durability-test" and key "backups/2026-07/large-file.dat" with the upload ID recorded
      And part number 1 is uploaded and its ETag and size are recorded in the upload session
      When the application process is stopped and started again with the same filesystem root
      Then listing multipart uploads for bucket "ep2-mpu-durability-test" includes the recorded upload ID
      And the recorded upload ID can be used to abort the multipart upload
      And after the abort, listing multipart uploads for bucket "ep2-mpu-durability-test" does not include the recorded upload ID

    @REQ-DUR-002 @functional-requirement @in-memory-exemption @implemented-not-e2e-validated
    Scenario: In-memory single-node mode is exempt from multipart durability
      Given the in-memory single-node profile is active
      And a multipart upload is initiated for bucket "ep2-mpu-memory-test" with the upload ID recorded
      When the application process is stopped and started again
      Then listing multipart uploads for bucket "ep2-mpu-memory-test" does not include the recorded upload ID

  Rule: Per-object configuration metadata must survive restart in storage-engine mode
    Legal hold, object lock configuration, retention period, object-level encryption
    configuration and restore state for one bucket/key are committed together as one
    durable, self-consistent document under "metadata/object-config".

    @REQ-DUR-003 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Object legal hold survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-legal-hold"
      And an object exists at bucket "ep2-obj-meta-test" and key "records/case-4711/legal-hold.dat"
      And the object has legal hold status "ON"
      When the application process is stopped and started again with the same filesystem root
      Then GetObjectLegalHold for key "records/case-4711/legal-hold.dat" returns status "ON"

    @REQ-DUR-003 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Object lock configuration survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-lock-config"
      And an object exists at bucket "ep2-obj-meta-test" and key "records/case-4711/locked-file.doc"
      And the object has lock configuration with mode "COMPLIANCE" and a retention period of 60 days
      When the application process is stopped and started again with the same filesystem root
      Then GetObjectLockConfiguration for key "records/case-4711/locked-file.doc" returns mode "COMPLIANCE" and a retention period of 60 days

    @REQ-DUR-003 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Object retention period survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-retention"
      And an object exists at bucket "ep2-obj-meta-test" and key "records/case-4711/retained.log"
      And the object has a retention period of 365 days
      When the application process is stopped and started again with the same filesystem root
      Then GetObjectRetention for key "records/case-4711/retained.log" returns a retention period of 365 days

    @REQ-DUR-003 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Object encryption configuration survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-encryption"
      And an object exists at bucket "ep2-obj-meta-test" and key "records/case-4711/encrypted.bin"
      And the object has server-side encryption configuration with algorithm "AES256"
      When the application process is stopped and started again with the same filesystem root
      Then the object encryption configuration for key "records/case-4711/encrypted.bin" returns algorithm "AES256"

    @REQ-DUR-003 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Object restore state survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-restore"
      And an object exists at bucket "ep2-obj-meta-test" and key "archives/2026/restored.zip"
      And the object has restore state requested at "2026-07-02T09:10:00Z" expiring at "2026-07-09T09:10:00Z" with tier "STANDARD"
      When the application process is stopped and started again with the same filesystem root
      Then the object restore state for key "archives/2026/restored.zip" returns the recorded request and expiry timestamps

    @REQ-DUR-003 @functional-requirement @durability @restart-safety @ep2-webclient-restart @ep2-full-process-restart @implemented-and-validated
    Scenario: Object tags survive restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-tags"
      And an object exists at bucket "ep2-obj-meta-test" and key "documents/tags-doc.pdf"
      And the object has tags "Project=Alpha" and "Version=2"
      When the application process is stopped and started again with the same filesystem root
      Then GetObjectTagging for key "documents/tags-doc.pdf" returns tags "Project=Alpha" and "Version=2"

    @REQ-DUR-003 @functional-requirement @durability @restart-safety @ep2-webclient-restart @ep2-full-process-restart @implemented-and-validated
    Scenario: Object ACL survives restart in storage-engine mode
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-003-acl"
      And an object exists at bucket "ep2-obj-meta-test" and key "documents/acl-file.txt"
      And the object has an ACL grant "FULL_CONTROL" for user "admin"
      When the application process is stopped and started again with the same filesystem root
      Then GetObjectAcl for key "documents/acl-file.txt" returns grant "FULL_CONTROL" for user "admin"

    @REQ-DUR-003 @functional-requirement @in-memory-exemption @implemented-not-e2e-validated
    Scenario: In-memory single-node mode is exempt from per-object metadata durability
      Given the in-memory single-node profile is active
      And an object exists at bucket "ep2-obj-meta-mem-test" and key "mem-only.txt"
      And the object has tags "Env=test"
      When the application process is stopped and started again
      Then GetObjectTagging for key "mem-only.txt" returns no tags or reports the object as absent

  Rule: Bucket configuration families stored on the bucket aggregate must survive restart
    Configuration families persisted through the bucket repository (the full BucketConfig
    document: CORS, policy, lifecycle, logging, website, notification, replication,
    request-payment, ownership controls, public access block, accelerate, analytics,
    inventory, metrics, intelligent tiering, encryption, ABAC, metadata and
    metadata-table configuration) are part of the durable bucket registry document.

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket CORS configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-cors"
      And bucket "ep2-bucket-config-test" has a CORS rule allowing origin "https://app.example.com" with methods "GET,PUT"
      When the application process is stopped and started again with the same filesystem root
      Then GetBucketCors for bucket "ep2-bucket-config-test" includes the rule allowing origin "https://app.example.com"

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket notification configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-notification"
      And bucket "ep2-bucket-config-test" has a notification configuration with topic destination "arn:aws:sns:eu-west-1:000000000000:ep2-topic"
      When the application process is stopped and started again with the same filesystem root
      Then GetBucketNotificationConfiguration for bucket "ep2-bucket-config-test" returns the topic destination "arn:aws:sns:eu-west-1:000000000000:ep2-topic"

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket object-lock configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-object-lock"
      And bucket "ep2-bucket-config-test" has an object-lock configuration with retention mode "GOVERNANCE" and a period of 30 days
      When the application process is stopped and started again with the same filesystem root
      Then GetObjectLockConfiguration for bucket "ep2-bucket-config-test" returns retention mode "GOVERNANCE" and a period of 30 days

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket inventory-table configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-inventory-table"
      And bucket "ep2-bucket-config-test" has an inventory-table configuration with id "ep2-inventory-table" format "Parquet" and schedule "Daily"
      When the application process is stopped and started again with the same filesystem root
      Then the inventory-table configuration for bucket "ep2-bucket-config-test" returns id "ep2-inventory-table"

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket journal-table configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-journal-table"
      And bucket "ep2-bucket-config-test" has a journal-table configuration with id "ep2-journal-table" format "Parquet" and schedule "Hourly"
      When the application process is stopped and started again with the same filesystem root
      Then the journal-table configuration for bucket "ep2-bucket-config-test" returns id "ep2-journal-table"

  Rule: ABAC, metadata and metadata-table configuration are durable on the bucket aggregate
    ABAC routing, bucket metadata configuration and metadata-table configuration were
    previously held in handler-local in-memory maps inside S3BucketConfigHandler. They
    are now modelled on the bucket aggregate (matching the S3 API shape) and persisted
    through the durable bucket registry document, so no bucket configuration family
    remains as handler-local web-adapter state.

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket ABAC configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-abac"
      And bucket "ep2-bucket-config-test" has an ABAC rule granting "s3:GetObject" to principal "user/admin" gated by tag "role" equal to "admin"
      When the application process is stopped and started again with the same filesystem root
      Then the bucket ABAC configuration for "ep2-bucket-config-test" includes the rule granting "s3:GetObject" gated by tag "role" equal to "admin"

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket metadata configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-metadata"
      And bucket "ep2-bucket-config-test" has a metadata configuration rule "md-1" with status "Enabled" for resource type "OBJECT" subtype "TAGS"
      When the application process is stopped and started again with the same filesystem root
      Then the metadata configuration for bucket "ep2-bucket-config-test" includes rule "md-1" for resource type "OBJECT"

    @REQ-DUR-004 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-full-process-restart @implemented-and-validated
    Scenario: Bucket metadata-table configuration survives restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-004-metadata-table"
      And bucket "ep2-bucket-config-test" has a metadata-table configuration rule "mdt-1" with table name "ep2-metadata-table" in database "analytics-db"
      When the application process is stopped and started again with the same filesystem root
      Then the metadata-table configuration for bucket "ep2-bucket-config-test" returns table name "ep2-metadata-table"

  Rule: A combined restart scenario proves that no metadata family is silently lost
    One scenario exercises the bucket registry, per-object metadata and multipart
    state together across a single restart of the same filesystem root.

    @REQ-DUR-005 @functional-requirement @non-functional-requirement @durability @restart-safety @ep2-webclient-restart @ep2-full-process-restart @implemented-and-validated
    Scenario: All EP-2 metadata families survive one combined restart
      Given the storage-engine profile is active with filesystem root "target/storage-engine-it/REQ-DUR-005-combined"
      And bucket "ep2-combined-test" is created through the S3 CreateBucket API
      And an object exists at bucket "ep2-combined-test" and key "combined/object.bin" with tags "Env=prod" and an ACL grant "FULL_CONTROL" for user "admin"
      And a multipart upload is initiated for bucket "ep2-combined-test" and key "combined/large.bin" with the upload ID recorded
      When the application process is stopped and started again with the same filesystem root
      Then a HeadBucket request for bucket "ep2-combined-test" returns HTTP 200
      And GetObjectTagging for key "combined/object.bin" returns tags "Env=prod"
      And GetObjectAcl for key "combined/object.bin" returns grant "FULL_CONTROL" for user "admin"
      And listing multipart uploads for bucket "ep2-combined-test" includes the recorded upload ID
