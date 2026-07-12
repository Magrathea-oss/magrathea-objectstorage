# Object Storage Product Extension

Object Storage-owned Product Shell extension for the EP-7 Admin Control Plane.

It contributes dashboard, backend status, read-only policy/device/topology catalogs, non-persistent policy validation, bucket capacity/quota accounting, operational report, observability, and S3 diagnostic routes. Admin API and S3 Data Plane clients are separate typed ports: S3 diagnostics cannot target Admin or private storage-engine routes.

Storage-policy and topology catalogs are presented as read-only configuration-as-code. Generated `dist/` output is produced only by the workspace build and is not source.
