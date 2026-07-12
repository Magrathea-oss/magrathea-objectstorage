@requirement @phase-ep7 @admin-control-plane @business-need
Business Need: EP-7 Object Storage administration through the Magrathea Product Shell
  Object Storage administrators need an accessible operational application that presents
  only evidence supplied by the Admin Control Plane and uses the S3 Data Plane for S3
  diagnostics. The application must not turn read-only configuration catalogs into an
  apparent mutation interface or create a privileged alternate object API.

  Validation roles:
    - Frontend end-to-end validation exercises the built application with keyboard, desktop,
      tablet, mobile, offline, and direct-navigation browser modes.
    - Admin API contract validation observes the production AdminRouter HTTP contracts.
    - S3 diagnostic contract validation observes the destination plane, authentication,
      request path, response, and audit evidence for a diagnostic request.

  Rule: Admin API contracts expose truthful control-plane evidence without a frontend

    @REQ-ADMIN-023 @functional-requirement @admin-api @backend-status @admin-api-contract-required @implemented-and-validated
    Scenario: Backend status identifies its configuration sources and runtime availability
      Given the Admin API runs with profile "storage-engine"
      And property "magrathea.object-store.backend" selects backend "storage-engine"
      And the YAML catalogs and storage root are configured as follows:
        | catalog        | item count | source directory                                      |
        | policies       | 2          | target/ep7-admin-api/config/storage-policies          |
        | devices        | 2          | target/ep7-admin-api/config/storage-devices           |
        | diskSets       | 1          | target/ep7-admin-api/config/disk-sets                 |
      And filesystem root "target/ep7-admin-api/storage-engine" is available
      And no latest recovery summary provider is configured
      When an Admin API client requests GET "/admin/backend-status"
      Then the Admin API response status is 200 with content type "application/json"
      And the response contains these backend-status fields:
        | JSON field                         | value                           |
        | selectedBackend                    | storage-engine                  |
        | selection.profile                  | storage-engine                  |
        | selection.property.name            | magrathea.object-store.backend  |
        | selection.property.value           | storage-engine                  |
        | catalogs.policies.itemCount         | 2                               |
        | catalogs.policies.sourceDirectory  | target/ep7-admin-api/config/storage-policies |
        | catalogs.devices.itemCount          | 2                               |
        | catalogs.devices.sourceDirectory   | target/ep7-admin-api/config/storage-devices  |
        | catalogs.diskSets.itemCount         | 1                               |
        | catalogs.diskSets.sourceDirectory  | target/ep7-admin-api/config/disk-sets        |
        | recoverySummary.availability        | not-configured                  |
      And storage root "target/ep7-admin-api/storage-engine" reports availability "available"
      And the response invents no recovery scan or finding counts

    @REQ-ADMIN-024 @functional-requirement @admin-api @operational-report @recovery @garbage-collection @scrub @audit @metrics @tracing @admin-api-contract-required @implemented-and-validated
    Scenario Outline: An operational report without a real provider is honestly unavailable
      Given no real "<report>" report provider is configured
      When an Admin API client requests GET "<path>"
      Then the Admin API response status is 503 with content type "application/json"
      And the response error code is "report-provider-not-configured"
      And the response error path is "<path>"
      And response field "error.details.reportType" is "<report>"
      And response field "error.details.availability" is "not-configured"
      And the response contains no sample records, generated values, filesystem inference, or healthy default

      Examples:
        | report             | path                                      |
        | recovery           | /admin/reports/recovery                   |
        | garbage-collection | /admin/reports/garbage-collection         |
        | scrub              | /admin/reports/scrub                      |
        | audit              | /admin/reports/audit                      |
        | metrics            | /admin/reports/metrics                    |
        | traces             | /admin/reports/traces                     |

    @REQ-ADMIN-025 @functional-requirement @admin-api @storage-policy @storage-policy-catalog @admin-api-contract-required @implemented-and-validated
    Scenario: Policy catalog responses preserve configured pipeline semantics
      Given the YAML policy catalog contains "MINIO_STANDARD" with erasure coding 4 data blocks and 2 parity blocks and replication factor 1
      When an Admin API client requests GET "/admin/storage-policies"
      Then the Admin API response status is 200 with content type "application/json"
      And the response count matches the number of configured policies
      And policy "MINIO_STANDARD" reports erasure coding 4 data blocks and 2 parity blocks and replication factor 1
      And policy "MINIO_STANDARD" omits deduplication, compression, and encryption stages that are not configured
      When the client requests GET "/admin/storage-policies/minio-standard"
      Then the response identifies storage class "MINIO_STANDARD", erasure coding 4 data blocks and 2 parity blocks, and replication factor 1
      And its links identify the read-only collection and non-persistent validation endpoint

    @REQ-ADMIN-026 @functional-requirement @admin-api @storage-device @disk-set @topology @admin-api-contract-required @implemented-and-validated
    Scenario: Device and disk-set responses preserve capacity, eligibility, and topology
      Given the storage-device catalog contains device "node-1-disk-0" at "/data/node-1/disk-0" with health "DEGRADED"
      And device "node-1-disk-0" has total capacity 107374182400, available capacity 26843545600, read eligibility true, and write eligibility false
      And disk-set "rack-a" has failure domain "RACK" and members "node-1-disk-0" and "node-2-disk-0"
      When an Admin API client requests GET "/admin/storage-devices/node-1-disk-0"
      Then the response status is 200 and preserves the configured path, capacities, health, and eligibility
      When the client requests GET "/admin/disk-sets/rack-a"
      Then the response status is 200 and preserves failure domain "RACK" and both member identifiers
      And the responses link only to their read-only catalog resources

    @REQ-ADMIN-027 @functional-requirement @admin-api @catalog @fail-closed @admin-api-contract-required @implemented-and-validated
    Scenario Outline: A catalog without a configured provider fails closed
      Given no "<catalog>" catalog provider is configured
      When an Admin API client requests GET "<path>"
      Then the Admin API response status is 503 with content type "application/json"
      And the response error code is "catalog-not-configured"
      And the response error path is "<path>"
      And no catalog item is returned

      Examples:
        | catalog                | path                      |
        | storage-policy-catalog | /admin/storage-policies   |
        | storage-device-catalog | /admin/storage-devices    |
        | disk-set-catalog       | /admin/disk-sets          |

    @REQ-ADMIN-028 @functional-requirement @admin-api @storage-policy @validation @non-persistent @admin-api-contract-required @implemented-and-validated
    Scenario: Policy validation returns structured findings without changing the catalog
      Given the policy catalog contains only storage class "MINIO_STANDARD"
      When an Admin API client posts this proposal to "/admin/storage-policies/validate":
        """
        {
          "storageClassId": "ARCHIVE_EC",
          "erasureCoding": { "dataBlocks": 8, "parityBlocks": 4 },
          "replication": { "factor": 1 }
        }
        """
      Then the Admin API response status is 200 with content type "application/json"
      And the response reports validity true with no field errors
      And the response links to the validation endpoint and the read-only policy collection
      When the client requests GET "/admin/storage-policies"
      Then storage class "ARCHIVE_EC" is absent and "MINIO_STANDARD" remains unchanged

    @REQ-ADMIN-029 @functional-requirement @admin-api @storage-policy @configuration-as-code @admin-api-contract-required @implemented-and-validated
    Scenario Outline: Storage policy catalog mutation methods remain unavailable
      Given the Admin API uses a YAML-backed read-only storage-policy catalog
      When an Admin API client requests <method> "<path>"
      Then the Admin API response status is 405 with content type "application/json"
      And the response Allow header is "GET, HEAD"
      And the response error code is "admin-catalog-read-only"
      And the response explains that changes require configuration-as-code and catalog reload or redeployment

      Examples:
        | method | path                                      |
        | POST   | /admin/storage-policies                   |
        | PUT    | /admin/storage-policies/minio-standard    |
        | DELETE | /admin/storage-policies/minio-standard    |

    @REQ-ADMIN-030 @functional-requirement @admin-api @capacity @quota @admin-api-contract-required @implemented-and-validated
    Scenario: Bucket capacity and quota administration expose accounting rather than object access
      Given bucket "archive-2026" has used bytes 7340032, reserved bytes 1048576, quota bytes 10737418240, 2 rejected reservations, and last rejected bytes 2097152
      When an Admin API client requests GET "/admin/buckets/archive-2026/capacity"
      Then the Admin API response status is 200 with content type "application/json"
      And the response identifies bucket "archive-2026" and preserves every capacity accounting value
      When the client requests PUT "/admin/buckets/archive-2026/quota" with quota bytes 21474836480
      Then the response status is 200 and quota bytes are 21474836480
      And a subsequent capacity response preserves usage, reservations, and rejection counters
      And neither response contains bucket contents, object keys, or object-operation links

    @REQ-ADMIN-031 @functional-requirement @admin-api @route-inventory @s3-data-plane @security @admin-api-contract-required @implemented-and-validated
    Scenario: The Admin route inventory contains no bucket or object data plane
      Given the production AdminRouter is the Admin Control Plane route source
      When the Admin API contract runner inventories its HTTP methods and path predicates
      Then the inventory contains only these route families:
        | methods           | path pattern                              | purpose                              |
        | GET               | /admin/health                             | API metadata                         |
        | GET               | /admin/live                               | liveness                             |
        | GET               | /admin/ready                              | readiness                            |
        | GET               | /admin/backend-status                     | backend and configuration status     |
        | GET, POST         | /admin/storage-policies                   | read catalog or reject mutation      |
        | GET, PUT, DELETE  | /admin/storage-policies/{id}              | read catalog item or reject mutation |
        | POST              | /admin/storage-policies/validate          | non-persistent validation            |
        | GET               | /admin/storage-devices                    | read device catalog                  |
        | GET               | /admin/storage-devices/{id}               | read device catalog item             |
        | GET               | /admin/disk-sets                          | read disk-set catalog                |
        | GET               | /admin/disk-sets/{id}                     | read disk-set catalog item           |
        | GET               | /admin/buckets/{bucket}/capacity          | capacity accounting                  |
        | PUT               | /admin/buckets/{bucket}/quota             | quota administration                 |
        | GET               | /admin/reports/recovery                   | recovery report or unavailable       |
        | GET               | /admin/reports/garbage-collection         | GC report or unavailable             |
        | GET               | /admin/reports/scrub                      | scrub report or unavailable          |
        | GET               | /admin/reports/audit                      | audit report or unavailable          |
        | GET               | /admin/reports/metrics                    | metrics report or unavailable        |
        | GET               | /admin/reports/traces                     | trace report or unavailable          |
      And no route creates, lists, reads, writes, or deletes buckets or objects
      And no route exposes multipart, metadata, tagging, ACL, versioning, or other S3 object semantics
      And no route exposes a storage-engine object, chunk, manifest, filesystem, or private repository API

  Rule: The dashboard reports only observable Admin Control Plane state

    @REQ-ADMIN-001 @functional-requirement @admin-ui @health @readiness @frontend-e2e-required @implemented-and-validated
    Scenario: Dashboard distinguishes liveness from readiness and identifies unavailable catalogs
      Given the Object Storage extension is opened at route "/admin"
      And "GET /admin/health" reports status "ok" and mode "configuration-as-code"
      And "GET /admin/ready" reports status "not-ready" with component "storage-device-catalog" status "not-configured"
      When the administrator views the dashboard
      Then the dashboard shows the Admin API as live but not ready
      And the unavailable component is named "storage-device-catalog"
      And the dashboard does not replace the unavailable result with a healthy default

    @REQ-ADMIN-002 @functional-requirement @admin-ui @backend-status @frontend-e2e-required @admin-api-contract-required @implemented-and-validated
    Scenario: Dashboard explains selected backend and catalog sources when backend status is available
      Given profile "storage-engine" selects backend "storage-engine"
      And "GET /admin/backend-status" reports the selecting property, catalog counts, catalog source locations, storage roots, and their availability
      When the administrator opens route "/admin/backend-status"
      Then the selected backend and selecting profile or property are visible
      And policy, device, and disk-set catalog counts and source locations are visible
      And every configured storage root is shown with its availability
      And the page identifies unavailable fields instead of inventing values

  Rule: Storage policy administration preserves configuration-as-code semantics

    @REQ-ADMIN-003 @functional-requirement @admin-ui @admin-api @storage-policy @storage-policy-catalog @frontend-e2e-required @implemented-and-validated
    Scenario: Administrator reads the MINIO_STANDARD policy without mutation controls
      Given "GET /admin/storage-policies" includes storage class "MINIO_STANDARD"
      And "GET /admin/storage-policies/minio-standard" reports its deduplication, compression, encryption, erasure-coding, and replication pipeline
      When the administrator follows the policy link to route "/admin/storage-policies/minio-standard"
      Then the policy detail shows every configured pipeline stage returned by the Admin API
      And the page states "Read-only configuration-as-code"
      And the page states that changes require YAML configuration followed by catalog reload or redeployment
      And create, edit, save, and delete policy actions are not offered

    @REQ-ADMIN-004 @functional-requirement @admin-ui @admin-api @storage-policy @validation @frontend-e2e-required @implemented-and-validated
    Scenario: Administrator validates a proposed policy without persisting it
      Given the policy catalog contains only storage class "MINIO_STANDARD"
      And the administrator enters proposed storage class "ARCHIVE_EC" with erasure coding 8 data blocks and 4 parity blocks
      When the application posts the proposal to "/admin/storage-policies/validate"
      Then the application labels the result as validation only and non-persistent
      And the structured validity result and field errors are displayed
      And a subsequent "GET /admin/storage-policies" does not contain "ARCHIVE_EC"
      And the application does not send POST, PUT, or DELETE to a storage-policy catalog mutation route

    @REQ-ADMIN-005 @functional-requirement @admin-ui @admin-api @storage-policy @offline @frontend-e2e-required @implemented-and-validated
    Scenario: Policy catalog failure uses a truthful recoverable state
      Given the administrator opens route "/admin/storage-policies"
      And "GET /admin/storage-policies" returns error code "catalog-unavailable"
      When the policy page finishes loading
      Then an error state identifies the storage-policy catalog as unavailable
      And a keyboard-operable retry action is offered
      And stale policy rows are not presented as current configuration

  Rule: Devices and disk-set topology are read-only operational catalogs

    @REQ-ADMIN-006 @functional-requirement @admin-ui @admin-api @storage-device @frontend-e2e-required @implemented-and-validated
    Scenario: Administrator inspects device capacity, health, and eligibility
      Given "GET /admin/storage-devices" contains device "node-1-disk-0" at path "/data/node-1/disk-0"
      And the device reports total and available capacity, health "DEGRADED", and read and write eligibility
      When the administrator opens route "/admin/storage-devices/node-1-disk-0"
      Then the device path, capacity, health, and eligibility are visible
      And the page provides no create, edit, retire, or delete device action
      And the page identifies the catalog as read-only configuration-as-code

    @REQ-ADMIN-007 @functional-requirement @admin-ui @admin-api @disk-set @topology @frontend-e2e-required @implemented-and-validated
    Scenario: Administrator follows disk-set membership into device details
      Given "GET /admin/disk-sets/rack-a" reports failure domain "RACK" and devices "node-1-disk-0" and "node-2-disk-0"
      When the administrator opens route "/admin/disk-sets/rack-a"
      Then the failure domain and both member identifiers are visible
      And each member links to its storage-device detail route
      And the topology page offers no disk-set or membership mutation action

  Rule: Operational views never fabricate unsupported Admin API evidence

    @REQ-ADMIN-008 @functional-requirement @admin-ui @capacity @frontend-e2e-required @implemented-and-validated
    Scenario: Capacity view reports the bucket capacity contract that exists
      Given "GET /admin/buckets/archive-2026/capacity" reports used bytes 7340032, reserved bytes 1048576, quota bytes 10737418240, and 2 rejected reservations
      When the administrator opens route "/admin/capacity/archive-2026"
      Then used, reserved, quota, and rejected-reservation values are visibly distinguished
      And the capacity view identifies "archive-2026" as the selected bucket
      And the view does not offer object or bucket browsing

    @REQ-ADMIN-009 @functional-requirement @admin-ui @recovery @garbage-collection @scrub @frontend-e2e-required @admin-api-contract-required @implemented-and-validated
    Scenario: Recovery, garbage-collection, and scrub views present unavailable reports honestly
      Given the Admin API report contracts for recovery, garbage collection, and scrub return error code "report-provider-not-configured" with availability "not-configured" because no providers are configured
      When the administrator opens route "/admin/data-hygiene"
      Then the application shows those reports as unavailable
      And it does not infer findings from health, route inventory, filesystem paths, or static examples
      And no recovery, garbage-collection, or scrub action is enabled

    @REQ-ADMIN-010 @functional-requirement @admin-ui @audit @metrics @observability @frontend-e2e-required @admin-api-contract-required @implemented-and-validated
    Scenario: Audit and observability views expose only authorized Admin API records
      Given the Admin API report contracts for audit, metrics, and traces return error code "report-provider-not-configured" with availability "not-configured" because no providers are configured
      When the administrator opens route "/admin/observability"
      Then audit, metric, and trace panels are shown as unavailable
      And no sample event, generated chart, or hard-coded healthy value is presented as runtime evidence
      And the application does not read storage-engine files or internal observability ports directly

  Rule: S3 diagnostics remain ordinary S3 Data Plane requests

    @REQ-ADMIN-011 @functional-requirement @security @admin-ui @s3-diagnostics @s3-data-plane @frontend-e2e-required @s3-diagnostic-contract-required @implemented-and-validated
    Scenario: Diagnostic HeadObject uses S3 authentication and never an Admin object route
      Given the administrator is authorized to run an S3 diagnostic with credential profile "tenant-a-readonly"
      And bucket "diagnostics-2026" contains object "probes/readiness.txt"
      When the administrator requests a HeadObject diagnostic for that bucket and key
      Then the application sends a signed HeadObject request to the configured S3 Data Plane endpoint
      And the diagnostic displays the S3 request id, status, ETag, content length, and S3 error code when present
      And no request is sent to an "/admin/objects", "/admin/buckets/diagnostics-2026/objects", storage-engine port, or private storage-engine endpoint
      And the diagnostic receives no authorization privilege beyond credential profile "tenant-a-readonly"

  Rule: Navigation remains usable across routes, input methods, and viewports

    @REQ-ADMIN-012 @functional-requirement @non-functional-requirement @accessibility @deep-linking @frontend-e2e-required @implemented-and-validated
    Scenario Outline: A copied detail URL restores navigable page context
      Given the administrator starts a new browser session at "<route>"
      When the requested resource is loaded from its backing Admin API endpoint
      Then the named detail page is displayed without first visiting the dashboard
      And the page title and primary heading identify "<resource>"
      And breadcrumbs provide keyboard-operable navigation to its collection and the dashboard
      And browser back and forward navigation preserve the selected route

      Examples:
        | route                                          | resource          |
        | /admin/storage-policies/minio-standard         | MINIO_STANDARD    |
        | /admin/storage-devices/node-1-disk-0           | node-1-disk-0     |
        | /admin/disk-sets/rack-a                        | rack-a            |

    @REQ-ADMIN-013 @functional-requirement @non-functional-requirement @accessibility @responsive @frontend-e2e-required @implemented-and-validated
    Scenario Outline: Primary administration remains keyboard accessible at supported widths
      Given the Object Storage application viewport is <width> pixels wide
      And focus starts at the browser content boundary
      When the administrator uses only Tab, Shift+Tab, Enter, Space, Escape, and arrow keys
      Then a skip link moves focus to the main content
      And every visible navigation item and page action has a visible focus indicator
      And the current route is exposed programmatically
      And no content or action requires horizontal page scrolling at <width> pixels
      And status is conveyed by text or programmatic name rather than color alone

      Examples:
        | width |
        | 360   |
        | 768   |
        | 1440  |

  Rule: The administration experience is task-oriented, polished, and truthful

    @REQ-ADMIN-032 @functional-requirement @admin-ui @dashboard @navigation @usability @frontend-e2e-required @implemented-and-validated
    Scenario: Dashboard leads administrators from operational priorities to relevant tasks
      Given readiness is degraded by storage device "node-1-disk-0"
      And backend "storage-engine" is selected
      And policy validation and bucket quota administration are available
      When the administrator opens route "/admin"
      Then current readiness, the selected backend, and conditions requiring attention have the strongest information priority
      And navigation groups destinations by the tasks of assessing service health, inspecting storage, validating policy, and managing capacity
      And the degraded device provides a direct path to its detail page
      And no dashboard card or navigation item offers bucket or object operations outside the S3 Data Plane

    @REQ-ADMIN-033 @functional-requirement @non-functional-requirement @admin-ui @affordance @configuration-as-code @usability @frontend-e2e-required @implemented-and-validated
    Scenario: Affordances communicate what can be done before an administrator acts
      Given storage policy and topology catalogs are read-only configuration-as-code
      And policy validation is non-persistent
      And bucket quota administration is available
      When the administrator reviews the related pages
      Then links, actions, and read-only values are visually distinguishable and have labels that state their outcome
      And policy validation is identified as non-persistent before it is submitted
      And quota administration is presented as an Admin Control Plane action
      And unsupported catalog mutation and object-management controls are not presented as available actions

    @REQ-ADMIN-034 @functional-requirement @non-functional-requirement @admin-ui @progressive-disclosure @information-hierarchy @usability @frontend-e2e-required @implemented-and-validated
    Scenario: Operational pages reveal detail without obscuring the decision summary
      Given backend status contains a selected backend, readiness conditions, catalog sources, storage roots, and diagnostic details
      When the administrator opens route "/admin/backend-status"
      Then the selected backend, readiness impact, and conditions requiring action are understandable without expanding technical detail
      And catalog source, storage-root, and diagnostic details can be revealed in their operational context
      And critical or unavailable conditions are never hidden only because supporting detail is collapsed
      And revealing or hiding detail preserves the page route, heading context, and keyboard focus

    @REQ-ADMIN-035 @non-functional-requirement @admin-ui @responsive @information-hierarchy @visual-design @accessibility @frontend-e2e-required @visual-regression-required @implemented-and-validated
    Scenario Outline: Responsive pages retain a professional and scannable information hierarchy
      Given the Object Storage application viewport is <width> pixels wide
      And the dashboard contains health, attention, task, and supporting-detail content
      When the administrator views and traverses the dashboard
      Then page purpose, current status, primary tasks, and supporting detail remain ordered by importance
      And typography, spacing, grouping, and status treatment consistently distinguish that hierarchy without relying on color alone
      And dense content adapts without overlap, clipping, unreadable text, or horizontal page scrolling
      And contextual actions remain adjacent to the content they affect

      Examples:
        | width |
        | 360   |
        | 768   |
        | 1440  |

    @REQ-ADMIN-036 @functional-requirement @non-functional-requirement @admin-ui @operation-feedback @accessibility @frontend-e2e-required @implemented-and-validated
    Scenario: Policy validation gives timely and unambiguous operation feedback
      Given the administrator has entered a proposed policy "ARCHIVE_EC"
      When the administrator submits it for non-persistent validation
      Then the application promptly identifies the validation as in progress and prevents an accidental duplicate submission
      And completion is announced and shown as either a successful validation or an actionable failure
      And successful feedback reiterates that "ARCHIVE_EC" was not persisted
      And failure feedback preserves the proposal, explains what can be corrected or retried, and does not imply that the catalog changed

    @REQ-ADMIN-037 @functional-requirement @non-functional-requirement @admin-ui @unavailable-state @truthful-ui @usability @frontend-e2e-required @admin-api-contract-required @implemented-and-validated
    Scenario: Unavailable capabilities explain impact and valid next steps without false promise
      Given recovery, scrub, and metrics report providers return availability "not-configured"
      When the administrator views the affected operational pages
      Then each unavailable state names the capability, explains why evidence cannot be shown, and states the operational impact
      And any next step points only to valid configuration, documentation, retry, or navigation that the administrator can use
      And unavailable operations are not styled or announced as enabled actions
      And no empty chart, sample finding, healthy default, or generic success state is presented as runtime evidence
