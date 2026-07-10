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
