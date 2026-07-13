@business-need @requirement @phase-ep10 @s3-api @multi-node @cluster
Business Need: Preserve S3 bucket and whole-object behavior across one node loss in a fixed three-node cluster
  As an S3-compatible client,
  I want acknowledged bucket and whole-object writes to remain exact and readable after the coordinating node stops,
  So that the first EP-10 cluster slice proves consensus-selected metadata and quorum-durable replicas without exposing an internal object API.

  This shared feature is the executable source of truth for externally observable S3 behavior in the first
  fixed A/B/C cluster slice. WebTestClient and AWS CLI runners must select the same scenarios and differ only
  through glue, profiles, tags, and validation adapters. Internal root, Ratis, and replica inspection is
  supplementary evidence and cannot replace S3 CreateBucket, PutObject, and GetObject outcomes. The
  implemented-and-validated status applies only to this fixed first slice; it does not claim completion of
  broader EP-10 transfer, lifecycle, or compatibility capabilities. REQ-CLUSTER-019 and REQ-CLUSTER-020
  cover bounded current-generation whole-object repair and are implemented and validated through both
  real-process WebTestClient and AWS CLI modes. They do not authorize orphan cleanup, rebalance, periodic
  anti-entropy, dynamic membership, or erasure coding.

  The first-slice object fixture is the repository file
  "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin": it is 134 bytes,
  has SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800",
  and has whole-object MD5/ETag value "1ae14a405348c337d00821e272868e71".

  The fixed acceptance topology is:
    | node | stable node UUID                     | S3 endpoint            | Ratis control address | replica gRPC address | scenario root                         |
    | A    | 11111111-1111-4111-8111-111111111111 | https://127.0.0.1:19001 | 127.0.0.1:19801       | 127.0.0.1:19901     | target/ep10/three-node/node-a         |
    | B    | 22222222-2222-4222-8222-222222222222 | https://127.0.0.1:19002 | 127.0.0.1:19802       | 127.0.0.1:19902     | target/ep10/three-node/node-b         |
    | C    | 33333333-3333-4333-8333-333333333333 | https://127.0.0.1:19003 | 127.0.0.1:19803       | 127.0.0.1:19903     | target/ep10/three-node/node-c         |

  Every node has separate "identity", "ratis", "objects", "temporary", and "runtime" directories beneath
  its scenario root. The identical bootstrap manifest declares exactly A, B, and C as Ratis voters. Static
  bootstrap is a first-slice constraint, not dynamic membership support. The replicated policy is N=3/W=2,
  and degraded writes are disabled.

  Rule: The fixed control group orders the bucket namespace through the S3 API

    @REQ-CLUSTER-001 @functional-requirement @non-functional-requirement @consistency @consensus @create-bucket @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: A bucket created through node A is resolved through node B
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-001"
      And clean fixed cluster nodes A, B, and C run with the declared UUIDs, ports, roots, and three-voter bootstrap manifest
      And nodes A, B, and C have established one Ratis control group with an available control quorum
      When the S3 client sends an unconditional CreateBucket request for bucket "ep10-quorum-archive" to node A
      Then the S3 response reports successful bucket creation only after its bucket generation is consensus committed
      When the same S3 client sends HeadBucket for "ep10-quorum-archive" to node B
      Then node B reports that bucket "ep10-quorum-archive" exists
      And no direct Ratis or replica-gRPC call is used as S3 behavior evidence

      @webclient
      Examples: Multi-node WebTestClient validation
        | validation_mode           |
        | multi-node-webtestclient  |

      @awscli
      Examples: Multi-node AWS CLI validation
        | validation_mode      |
        | multi-node-aws-cli   |

  Rule: A successful whole-object write remains exactly readable after its coordinator stops

    @REQ-CLUSTER-002 @functional-requirement @non-functional-requirement @durability @integrity @availability @consistency @write-quorum @failover @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Node B returns exact committed bytes after node A coordinates the write and stops
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-002"
      And clean fixed cluster nodes A, B, and C run with the declared UUIDs, ports, roots, and three-voter bootstrap manifest
      And bucket "ep10-quorum-archive" has a consensus-committed bucket generation
      And fixture "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin" has length 134 and SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      When the S3 client sends an unconditional PutObject to node A for bucket "ep10-quorum-archive" and key "evidence/2026/first-quorum-object.bin" with that fixture
      Then the successful PutObject response is returned only after at least 2 of the 3 selected nodes durably acknowledge checksum-valid immutable replicas
      And one consensus-committed object-reference generation names only durable replicas with length 134 and SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      And the object-reference generation is committed after the replica acknowledgement threshold is met
      And the response ETag is "1ae14a405348c337d00821e272868e71"
      When node A is stopped without changing the fixed membership
      And nodes B and C retain the two-voter control quorum
      And the S3 client sends GetObject for bucket "ep10-quorum-archive" and key "evidence/2026/first-quorum-object.bin" to node B
      Then the response body is byte-for-byte equal to the uploaded fixture
      And its length is 134
      And its SHA-256 is "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      And its ETag is "1ae14a405348c337d00821e272868e71"
      And node B resolves the consensus-committed generation rather than selecting a locally newer or uncommitted reference

      @webclient
      Examples: Multi-node WebTestClient validation
        | validation_mode           |
        | multi-node-webtestclient  |

      @awscli
      Examples: Multi-node AWS CLI validation
        | validation_mode      |
        | multi-node-aws-cli   |

  Rule: The S3 write path fails rather than publishing below either quorum

    @REQ-CLUSTER-003 @functional-requirement @non-functional-requirement @durability @integrity @no-degraded-writes @write-quorum @failure-handling @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: One checksum-valid durable replica cannot satisfy W=2
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-003"
      And nodes A, B, and C have committed policy N=3/W=2 for bucket "ep10-no-degraded-data"
      And only node A can return a checksum-valid durable acknowledgement for key "failure/data-quorum.bin"
      And replica transfer to B exceeds its deadline while C rejects the staged bytes with a checksum mismatch
      When the S3 client sends an unconditional PutObject with the 134-byte fixture to node A
      Then the S3 write fails with an S3-compatible availability or internal failure response
      And no successful PutObject response or reduced-durability warning is returned
      And no object-reference generation for key "failure/data-quorum.bin" is consensus committed
      And GetObject for that key does not expose any staged replica
      And any durable unpublished artifact remains unreachable pending later fenced cleanup

      @webclient
      Examples: Multi-node WebTestClient validation
        | validation_mode           |
        | multi-node-webtestclient  |

      @awscli
      Examples: Multi-node AWS CLI validation
        | validation_mode      |
        | multi-node-aws-cli   |

    @REQ-CLUSTER-004 @functional-requirement @non-functional-requirement @consistency @split-brain-safety @control-quorum @no-degraded-writes @failure-handling @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Loss of two voters prevents reference publication even after two replicas are durable
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-004"
      And bucket "ep10-no-control-quorum" was committed while A, B, and C were available
      And nodes B and C are stopped so node A is the only available Ratis voter
      And two selected storage targets have durably staged checksum-valid replicas for key "failure/control-quorum.bin"
      When the S3 client sends an unconditional PutObject through node A
      Then the S3 write fails because the object-reference generation cannot reach control quorum
      And no successful PutObject response is returned
      And the two staged replicas remain unreachable through GetObject
      And restarting a voter cannot reveal an object-reference generation that was never consensus committed

      @webclient
      Examples: Multi-node WebTestClient validation
        | validation_mode           |
        | multi-node-webtestclient  |

      @awscli
      Examples: Multi-node AWS CLI validation
        | validation_mode      |
        | multi-node-aws-cli   |

  Rule: Persisted cluster state survives a complete stop and restart

    @REQ-CLUSTER-005 @functional-requirement @non-functional-requirement @durability @restart-safety @node-identity @consensus @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: A complete A B C restart preserves identities control state and the readable object generation
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-005"
      And REQ-CLUSTER-002 has committed bucket "ep10-quorum-archive" and key "evidence/2026/first-quorum-object.bin"
      When nodes A, B, and C stop and discard process memory
      And nodes A, B, and C restart with their original non-empty scenario roots and a reordered seed list "C,A,B"
      Then each node recovers its original stable UUID from its "identity" directory
      And the reordered seeds do not rewrite the persisted three-voter Ratis membership or committed log state
      And the committed bucket and object-reference generations are recovered from persisted control state
      When the S3 client sends GetObject for the committed key to node B
      Then the response body is byte-for-byte equal to the fixture
      And its length, SHA-256, and ETag remain 134, "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800", and "1ae14a405348c337d00821e272868e71"

      @webclient
      Examples: Multi-node WebTestClient restart validation
        | validation_mode                   |
        | multi-node-webtestclient-restart  |

      @awscli
      Examples: Multi-node AWS CLI restart validation
        | validation_mode              |
        | multi-node-aws-cli-restart   |

  Rule: A missing promised local replica is repaired before its bytes become the response

    @REQ-CLUSTER-019 @functional-requirement @non-functional-requirement @repair @durability @integrity @availability @consistency @single-pass-get @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: GET through B durably restores its missing current-generation replica before source C stops
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-019"
      And clean fixed cluster nodes A, B, and C run with the declared UUIDs, ports, roots, and three-voter bootstrap manifest
      And fixture "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin" has length 134, SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800", and MD5/ETag "1ae14a405348c337d00821e272868e71"
      And current consensus-committed reference generation 7 for bucket "ep10-repair-archive" and key "evidence/2026/current-generation-repair.bin" names immutable whole-object artifact "whole-7f351d76-50d8-4f48-9b86-6f94e777a101" on B and C with those exact facts
      And "target/ep10/three-node/node-c/objects/whole-7f351d76-50d8-4f48-9b86-6f94e777a101.artifact" contains the checksum-valid fixture
      But promised path "target/ep10/three-node/node-b/objects/whole-7f351d76-50d8-4f48-9b86-6f94e777a101.artifact" is missing before response streaming starts
      When the S3 client sends GetObject for that bucket and key to node B
      Then B consensus-commits one deduplicated repair job bound to generation 7, that artifact, and target UUID "22222222-2222-4222-8222-222222222222"
      And a current fenced claim owns the direct fetch from the different named source C
      And B completely verifies length 134 and SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800" before atomically publishing the target with file and parent-directory fsync
      And unverified or partially transferred bytes are never published or returned
      And after durable publication B opens the repaired local artifact once for the response while calculating length and checksum incrementally
      And B does not completely preflight-read a local payload and then open it a second time for the response
      And the response body is byte-for-byte equal to the fixture with length 134, SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800", and ETag "1ae14a405348c337d00821e272868e71"
      And current-token completion is consensus committed as "SUCCEEDED" without changing reference generation 7 or its replica set
      When source node C stops and nodes A and B retain control quorum
      And the S3 client sends a later GetObject for the same bucket and key to node B
      Then the later response again returns the exact fixture facts and ETag from B's durable repaired path
      And no remote replica read or unavailable source C is needed for the later response

      @webclient
      Examples: Multi-node WebTestClient repair validation
        | validation_mode          |
        | multi-node-webtestclient |

      @awscli
      Examples: Multi-node AWS CLI repair validation
        | validation_mode     |
        | multi-node-aws-cli  |

  Rule: Corruption discovered during the one response pass fails now and repairs only a later GET

    @REQ-CLUSTER-020 @functional-requirement @non-functional-requirement @repair @durability @integrity @consistency @single-pass-get @failure-handling @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: GET through B reports streamed corruption and succeeds only after durable repair
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CLUSTER-020"
      And clean fixed cluster nodes A, B, and C run with the declared UUIDs, ports, roots, and three-voter bootstrap manifest
      And fixture "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin" has length 134, SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800", and MD5/ETag "1ae14a405348c337d00821e272868e71"
      And current consensus-committed reference generation 8 for bucket "ep10-repair-archive" and key "evidence/2026/corrupt-current-generation.bin" names immutable whole-object artifact "whole-a26f054a-b8c4-48ba-8107-e2140e964202" on B and C with those exact facts
      And "target/ep10/three-node/node-c/objects/whole-a26f054a-b8c4-48ba-8107-e2140e964202.artifact" contains the checksum-valid fixture
      But the last byte at "target/ep10/three-node/node-b/objects/whole-a26f054a-b8c4-48ba-8107-e2140e964202.artifact" is changed from 0x0a to 0xf5, preserving length 134 but producing SHA-256 "92fdf1bf738ce40121046269e4db13013e3085208e16de8fce93385cde231180"
      And the repair worker is paused before it can claim newly ensured work
      When the S3 client sends GetObject for that bucket and key to node B
      Then B opens the present local payload once and calculates length and SHA-256 incrementally during that response pass
      And B does not completely preflight-read the payload and then open it a second time
      And the checksum mismatch terminates the current GET as an integrity failure even if response bytes or the expected ETag were already emitted
      And neither validation mode reports a successful 134-byte object or retains corrupt output as a successful download
      And B does not transparently retry source C for the already-started response
      And one repair job for generation 8, that artifact, and target B is durably consensus committed before repair is reported as scheduled
      And no GET is reported as successful merely because that repair job is "READY"
      When the repair worker claims that job and durably replaces B's corrupt path from a completely verified named replica on C
      And current-token completion is consensus committed as "SUCCEEDED"
      And the S3 client sends a later GetObject for the same bucket and key to node B
      Then the later response, and not the corrupt request, is the first successful GET after detection
      And its body is byte-for-byte equal to the fixture with length 134, SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800", and ETag "1ae14a405348c337d00821e272868e71"
      And reference generation 8 and its named replica set remain unchanged

      @webclient
      Examples: Multi-node WebTestClient corruption-repair validation
        | validation_mode          |
        | multi-node-webtestclient |

      @awscli
      Examples: Multi-node AWS CLI corruption-repair validation
        | validation_mode     |
        | multi-node-aws-cli  |

  Rule: Broader S3 transfer semantics remain explicit backlog

    @REQ-CLUSTER-006 @functional-requirement @multipart @later-slice @not-implemented
    Scenario: Multipart upload is not implied by the first whole-object cluster slice
      Given the fixed three-node profile implements only unconditional single-part whole-object PutObject
      When a client requests multipart initiation, part upload, completion, abort, or multipart-state failover
      Then EP-10 reports clustered multipart behavior as "not implemented"
      And no single-node multipart behavior is cited as multi-node validation

    @REQ-CLUSTER-007 @functional-requirement @broader-s3-compatibility @later-slice @not-implemented
    Scenario: Conditional versioned and chunked cluster writes remain outside the first slice
      Given the first slice covers only unconditional CreateBucket, PutObject, and whole-object GetObject
      When cluster status is reported
      Then conditional writes, S3 versioning, chunked-object transfer, and broader S3 compatibility are listed as later work
      And none is inferred from the first-slice acceptance proof
