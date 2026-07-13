workspace "Magrathea ObjectStore" "C4 model for the Magrathea S3-compatible object storage service" {

  !identifiers hierarchical

  model {
    user = person "User" "End user or automated client using AWS CLI, SDK, curl, or another S3-compatible HTTP client."
    administrator = person "Administrator" "Operator using the bundled Magrathea admin UI, generated documentation, and JSON management API."

    magrathea = softwareSystem "Magrathea ObjectStore" "AWS S3-compatible object storage and bundled admin/documentation UI built with Spring Boot 4 WebFlux and Java 21." {
      bootstrapApplication = container "bootstrap-application" "Executable Spring Boot application/fat JAR that composes the S3 API, admin API, object-store capabilities, and selectable persistence backends. It owns the static asset handoff: during generate-resources it builds/copies magrathea-ui dist assets plus generated docs/reports/C4 and arc42 images into bootstrap-application classpath static resources, while admin-api-adapter supplies only admin API routes." "Spring Boot 4 WebFlux, Reactor Netty, Maven resources/exec/antrun, Node.js/Vite" {
        magratheaApplication = component "MagratheaApplication" "Spring Boot entry point and component-scan boundary for bootstrap, object-store, storage-engine, S3 API, and admin API packages. Activates the single-node in-memory backend by default and the storage-engine backend via profile/property." "Spring Boot @SpringBootApplication"
        adminServerConfig = component "AdminServerConfig" "Starts the second reactive HTTP server on admin.server.port (default 8081), combines AdminRouter routes with classpath static resource routes for /, /assets/**, /docs/**, favicon.svg, icons.svg, and SPA fallback." "Spring @Configuration, Reactor Netty, RouterFunction"
        staticResources = component "Bootstrap static resources" "Bundled UI and documentation resources under bootstrap-application classpath static locations: magrathea-ui dist assets, generated documentation JSON, C4 images, arc42 images, and report assets. This is the target of the staged static asset handoff; admin-api-adapter does not own or serve these files directly." "Static HTML/CSS/JS/JSON/images"
        backendSelection = component "ObjectStoreBackendStatusConfig" "Validates and logs the selected object-store backend. The single-node profile uses in-memory repositories; the storage-engine profile/property selects the Storage Engine-backed repositories." "Spring @Configuration"
        clusterNodeRuntimeLifecycle = component "ClusterNodeRuntime lifecycle" "SmartLifecycle composition authority for one fixed-node JVM. It creates one process-session UUID; assembles the voter, replica server and clients, repair coordinator, worker, scheduler, and process-local metrics; starts repair scheduling only after the voter and replica server; and closes scheduling before transport shutdown. Lifecycle and wake signals carry no durable repair ownership: committed Ratis state does." "Spring SmartLifecycle, Java 21"
      }

      browserFrontend = container "Object Storage Admin Browser Application" "Browser runtime for the independently built Object Storage product composition. The deployed static distribution composes the product-neutral Product Shell with the Object Storage Product Extension and application-owned browser adapters. It calls the Admin Control Plane and, only when a separate endpoint and in-memory signer are configured, the S3 Data Plane for HeadObject diagnostics." "Vue 3, TypeScript, JavaScript, Vue Router, Vite" {
        productApplication = component "Object Storage Admin Application" "Deployable product composition in apps/object-storage-admin. Registers the Object Storage Product Extension, composes its navigation and routes into Product Shell, owns documentation routes, and wires browser/platform adapters through dependency injection." "Vue 3 application"
        productShell = component "Product Shell" "Product-neutral @magrathea/product-shell package. Provides extension contracts and deterministic composition, application/resource-state contracts, localization boundaries, accessible shell components, and semantic theme tokens. It contains no product routes, product data, network access, browser storage, or document mutation." "Vue 3, TypeScript library"
        objectStorageExtension = component "Object Storage Product Extension" "Product-owned @magrathea/object-storage-extension package. Contributes admin screens, routes, navigation, localization, catalog and capacity presentation, unavailable-provider states, and the optional S3 HeadObject diagnostic. It consumes typed clients rather than accessing private storage-engine routes." "Vue 3, TypeScript library"
        browserPlatformAdapters = component "Browser Platform Adapters" "Application-owned adapters for public endpoint metadata, connectivity, locale preference in localStorage, document language/title, navigation focus, and the optional in-memory S3 request signer. Credentials are not read from runtime metadata or browser storage." "Browser APIs, Vue Router"
        adminApiClient = component "Admin API Client" "Typed Object Storage extension adapter for health/readiness, backend status, read-only catalogs, non-persistent policy validation, bucket capacity/quota, and operational report routes. Preserves explicit unavailable and error responses from the Admin Control Plane." "TypeScript Fetch API adapter"
        s3HeadObjectClient = component "S3 HeadObject Client" "Typed, separately configured S3 Data Plane adapter limited to signed HeadObject diagnostics. Rejects Admin and private storage-engine target paths and is absent unless both an endpoint and signer are configured." "TypeScript Fetch API adapter"
        documentationUi = component "Documentation Viewer" "Application-owned routes and components for bundled generated product, architecture, test, API, and ADR documentation." "Vue 3 components"
      }

      adminApiAdapter = container "admin-api-adapter" "Management HTTP adapter that exposes the JSON Admin Control Plane for health/readiness, backend status, read-only catalogs, non-persistent policy validation, capacity/quota, and operational-report availability. Frontend/static documentation assets are handed off to bootstrap-application and served from its classpath static resources." "Spring Boot 4 WebFlux, RouterFunction" {
        adminRouter = component "AdminRouter" "Defines the /admin JSON route families. Catalog and capacity routes use configured storage-engine ports; backend status uses its configured provider; operational report routes return explicit provider-not-configured responses when no matching real provider exists." "Spring @Configuration, RouterFunction"
      }

      s3ReactiveApiAdapter = container "s3-reactive-api-adapter" "HTTP adapter that exposes the S3-compatible REST API and translates HTTP/XML/binary requests into reactive application use cases." "Spring Boot 4 WebFlux, RouterFunction, Java 21, Jackson XML" {
        bucketOperationsHandler = component "S3BucketOperationsHandler" "Handles bucket lifecycle endpoints: create, delete, head, list, location, versioning and directory-bucket listing." "Java WebFlux handler"
        bucketMetadataHandler = component "S3BucketMetadataHandler" "Handles bucket metadata endpoints such as ACL and tagging." "Java WebFlux handler"
        bucketConfigHandler = component "S3BucketConfigHandler" "Handles bucket configuration endpoints using registry/strategy dispatch pattern: CORS, lifecycle, policy, encryption, logging, website, notification, replication, request payment, ownership controls, public access block, accelerate, analytics, inventory, metrics, intelligent-tiering, ABAC, object lock, bucket metadata configuration, metadata table configuration, inventory table configuration, journal table configuration." "Java WebFlux handler, ConfigHandlerRegistry"
        objectOperationsHandler = component "S3ObjectOperationsHandler" "Handles object operations: put, get, head, delete, copy, multi-delete, rename, torrent, restore, select, and Object Lambda response." "Java WebFlux handler"
        objectMetadataHandler = component "S3ObjectMetadataHandler" "Handles object metadata endpoints such as ACL, tagging, attributes, legal hold, retention, and encryption metadata." "Java WebFlux handler"
        multipartHandler = component "S3MultipartHandler" "Handles multipart upload lifecycle: initiate, upload part, list parts, complete, abort and list uploads." "Java WebFlux handler"
        sessionHandler = component "S3SessionHandler" "Handles CreateSession for session management." "Java WebFlux handler"
        s3ProxyRouter = component "S3ProxyRouter" "Entry point RouterFunction: defines all S3 routes and dispatches requests to handlers based on HTTP method, path, query parameters, and headers." "RouterFunction"
        s3WebSupport = component "S3WebSupport" "Shared request predicates, XML parsing, error response helpers, and S3 error serialization." "Java utility"
      }

      reactiveObjectStore = container "reactive-object-store" "Reactive object storage capability for object upload, download, metadata lookup, deletion, multipart workflows, legal hold, retention, torrent, restore, select, Object Lambda, rename, and encryption update using reactive services and domain aggregates." "Java 21 reactive application/domain services" {
        reactiveObjectService = component "ReactiveObjectService" "Reactive application service for object CRUD, metadata lookup, object listing, binary content retrieval, legal hold, retention, torrent, restore, select, Object Lambda, rename, and encryption update." "Spring @Service"
        reactiveMultipartUploadService = component "ReactiveMultipartUploadService" "Reactive application service for multipart upload sessions and part lifecycle." "Spring @Service"
        s3Object = component "S3Object" "Domain aggregate for object identity, bucket id, key, ETag, storage class, content metadata, legal hold, retention, and encryption. Enforces state transitions with withLegalHold/withoutLegalHold, withRetention/withoutRetention, withEncryption/withoutEncryption. Emits ObjectStoreEvent domain events on transitions." "Domain aggregate"
        multipartUpload = component "MultipartUpload" "Domain aggregate for multipart upload state, uploaded parts and completion/abort status. Emits ObjectStoreEvent domain events on transitions." "Domain aggregate"
        s3ObjectRepositoryPort = component "S3ObjectRepository port" "Domain repository port for object metadata and binary content persistence (including legal hold, retention, restore, select data)." "Domain repository interface"
        multipartUploadRepositoryPort = component "MultipartUploadRepository port" "Domain repository port for multipart upload persistence." "Domain repository interface"
        contentBoundary = component "DefaultS3ObjectWrite / DefaultS3ObjectContent" "Application-layer content boundary for carrying Flux<DataBuffer> without leaking reactive types into the domain model." "Java adapter"
      }

      reactiveBucketManagement = container "reactive-bucket-management" "Reactive bucket management capability for bucket lifecycle, metadata, configuration, CORS/versioning, bucket-level operations, ABAC, object lock, metadata configuration, and table configuration." "Java 21 reactive application/domain services" {
        reactiveBucketService = component "ReactiveBucketService" "Reactive application service for bucket lifecycle, versioning, CORS, lifecycle, policy, encryption, logging, website, notification, replication, request payment, ownership controls, public access block, accelerate, analytics, inventory, metrics, intelligent-tiering, ABAC, object lock, bucket metadata configuration, metadata table configuration, inventory table configuration, journal table configuration, and session management." "Spring @Service"
        bucket = component "Bucket" "Domain aggregate root for bucket identity, name, region, storage class, versioning/encryption state and dedicated configuration fields per config type (CorsConfiguration, BucketLifecycleConfiguration, BucketEncryptionConfiguration, BucketLoggingConfiguration, BucketWebsiteConfiguration, BucketNotificationConfiguration, BucketReplicationConfiguration, BucketPolicy, PublicAccessBlockConfiguration, BucketOwnershipControls, BucketRequestPaymentConfiguration, BucketAccelerateConfiguration, plus multi-instance maps for Analytics, Inventory, Metrics, Intelligent-Tiering). Each config feature has a dedicated with*() / without*() method. Emits specific ObjectStoreEvent subtypes per config change (BucketConfigurationChanged with full BucketConfig payload)." "Domain aggregate root"
        bucketRepositoryPort = component "BucketRepository port" "Domain repository port for bucket aggregate persistence (including ABAC, object lock, metadata/table configuration storage)." "Domain repository interface"
      }

      reactiveRepositoryApplication = container "reactive-repository-application" "Command/Query repository interfaces: BucketCommandRepository, BucketQueryRepository, S3ObjectCommandRepository, S3ObjectQueryRepository, MultipartUploadCommandRepository, MultipartUploadQueryRepository." "Java 21 CQS interfaces" {
        bucketCommandRepository = component "BucketCommandRepository" "Write-side repository interface for bucket aggregate persistence." "CQS command interface"
        bucketQueryRepository = component "BucketQueryRepository" "Read-side repository interface for bucket aggregate queries." "CQS query interface"
        s3ObjectCommandRepository = component "S3ObjectCommandRepository" "Write-side repository interface for S3Object aggregate persistence (including legal hold, retention, restore, select data)." "CQS command interface"
        s3ObjectQueryRepository = component "S3ObjectQueryRepository" "Read-side repository interface for S3Object aggregate queries." "CQS query interface"
        multipartUploadCommandRepository = component "MultipartUploadCommandRepository" "Write-side repository interface for MultipartUpload aggregate persistence." "CQS command interface"
        multipartUploadQueryRepository = component "MultipartUploadQueryRepository" "Read-side repository interface for MultipartUpload aggregate queries." "CQS query interface"
      }

      reactiveInfrastructure = container "reactive-infrastructure" "Reactive in-memory persistence: InMemoryReactiveBucketRepository, InMemoryReactiveS3ObjectRepository, InMemoryReactiveMultipartUploadRepository." "Java 21, Reactor, ConcurrentHashMap" "Database" {
        inMemoryReactiveBucketRepository = component "InMemoryReactiveBucketRepository" "In-memory reactive implementation of BucketCommandRepository and BucketQueryRepository. Stores bucket aggregates in ConcurrentHashMap structures, including session, ABAC, object lock, and metadata/table configuration state." "Spring @Repository"
        inMemoryReactiveS3ObjectRepository = component "InMemoryReactiveS3ObjectRepository" "In-memory reactive implementation of S3ObjectCommandRepository and S3ObjectQueryRepository. Stores object metadata and raw object bytes in ConcurrentHashMap structures, including legal hold, retention, restore, and select request/response state." "Spring @Repository"
        inMemoryReactiveMultipartUploadRepository = component "InMemoryReactiveMultipartUploadRepository" "In-memory reactive implementation of MultipartUploadCommandRepository and MultipartUploadQueryRepository. Internally stores multipart upload sessions in a ConcurrentHashMap structure." "Spring @Repository"
      }

      storageEngine = container "storage-engine" "Storage Engine bounded context: persistence pipeline, virtual devices, storage policies, optional deduplication, erasure coding, content-addressed storage, and manifest management. MINIO_STANDARD is modeled as STANDARD with dedup disabled, EC enabled (4 data / 2 parity), replication factor 1, compression disabled, and encryption disabled by default." "Java 21, domain-driven" {
        storageEngineDomain = component "storage-engine-domain" "Pure domain model: StoragePolicy, EffectiveStoragePolicy, VirtualDevice, DedupNamespace, WorkflowCompatibilityKey, DeviceConfigurationHash, StepPlan, StepExecutionTrace, ChunkPersistenceTrace, ObjectManifest, StorageDevice, DiskSet, and StoredObject aggregate. Zero framework dependencies. MINIO_STANDARD semantics are STANDARD with dedup disabled, EC enabled (4 data / 2 parity), replication factor 1, compression disabled, and encryption disabled by default." "Java 21 domain"
        storageEngineReactiveRepositoryApplication = component "storage-engine-reactive-repository-application" "Reactive ports, catalogs, and repository interfaces used by the Storage Engine application: StoragePolicyCatalog, StorageDeviceCatalog, DiskSetCatalog, StoredObjectRepository, ObjectManifestRepository, ContentAddressIndex, DataTransformPort, AlterationPort, ChunkStorePort, and ECOutcome. SHA-256 checksums are computed directly inside pipeline/filesystem components without a dedicated checksum abstraction." "Java 21 CQS/port interfaces"
        storageEngineReactiveApplication = component "storage-engine-reactive-application" "Reactive orchestration and use cases: ReactiveStorageOrchestrator, Chunker, and ApplicationChunkPayload coordinate domain planning with repository/catalog/adapter ports." "Spring @Service, Reactor"
        storageEngineReactiveInfrastructure = component "storage-engine-reactive-infrastructure" "Filesystem and YAML adapters/configuration: FileSystemStorageCluster, FileSystemStorageNode, FileSystemVirtualDeviceMapper, FileSystemContentAddressIndex, FileSystemManifestRepository, FileSystemStoredObjectRepository, YAML policy/device/disk-set catalogs, compression/encryption/EC/replication adapters, direct SHA-256 calculation in filesystem/pipeline components, and FaultInjectingStorageCluster chaos decorator. MINIO_STANDARD YAML enables EC (4 data / 2 parity) and disables dedup; the filesystem mapping does not assign erasure-coded shards to distinct physical failure domains." "Java 21, Reactor, YAML"
        storageEngineRepositoryAdapter = component "object-store-reactive-repository-storage-engine-infrastructure" "Anti-Corruption Layer + adapter: implements Object Store repository interfaces using the Storage Engine backend. Contains ObjectStoreToStorageEngineTranslator, StorageEngineReactiveS3ObjectRepository, StorageEngineReactiveBucketRepository, StorageEngineReactiveMultipartUploadRepository." "Spring @Repository, Reactor"
      }

      clusterNode = container "Cluster Node Runtime" "Fixed A/B/C voter/storage runtime for unconditional CreateBucket, single-part whole-object PUT/GET, and bounded repair of a missing or corrupt replica named by the current committed generation. Each JVM co-locates one Ratis voter, one replica data server, a request-facing repair coordinator, a process-local scheduler, and a fenced worker. Ratis owns durable repair identity, lifecycle, claims, results, and snapshot recovery; verified whole-object bytes remain on the direct replica path. It exposes no separate object API and excludes dynamic membership, clustered multipart/versioning, erasure coding, rebalance, broad or periodic anti-entropy discovery, and automated orphan cleanup." "Java 21, Apache Ratis 3.2.2, grpc-java 1.82.2, protobuf 3.25.8, mTLS" {
        tags "FixedClusterRuntime"
        storageEngineClusterApplication = component "storage-engine-cluster-application" "Transport-neutral CreateBucket and whole-object write/read coordination, pure storage placement, authoritative metadata and local-artifact ports, acknowledgement fencing, replica-transfer/read ports, and bounded repair use cases. Reactor-facing where needed; contains no Ratis, protobuf, generated-stub, or gRPC types." "Java 21 application module"
        clusterProtocol = component "cluster-protocol" "Versioned internal protobuf contracts and generated messages/stubs for health plus immutable-artifact stage and repair-read transfer. It contains no S3 facade, Spring application behavior, storage-policy decisions, or domain logic." "protobuf/protoc 3.25.8 module"
        clusterControlRatisInfrastructure = component "cluster-control-ratis-infrastructure" "Deterministic Ratis state machine and adapter for bucket/object references plus canonical repair jobs, lifecycle transitions, command deduplication, process-session and claim-generation fences, and generation obsolescence. It uses a fixed A/B/C bootstrap manifest, stable UUID identity recovery, persisted log/snapshots, and identity-bound control mTLS. Snapshot version 2 persists repair state and migrates version 1 with an empty job map. The state machine performs no filesystem or replica-network side effects; dynamic membership is outside its fixed-node control scope." "Apache Ratis 3.2.2, ratis-grpc"
        clusterDataGrpcInfrastructure = component "cluster-data-grpc-infrastructure" "Independent replica gRPC server/clients and filesystem artifact adapter: peer identity checks, bounded 64 KiB manual-flow-control reads, deadlines/cancellation, token-specific repair staging, incremental length/SHA-256 verification, file fsync, atomic replacement, and strict parent-directory fsync." "grpc-java 1.82.2, grpc-netty-shaded"
        repairCoordinator = component "ClusterRepairCoordinator" "Request-facing repair use case. Known local absence durably ensures the canonical current-generation job and invokes inline repair before response streaming. A single-pass integrity failure increments process-local metrics, durably ensures or re-evaluates the same job, and emits a message-free scheduler wake without converting the failed GET into success." "Java 21, Reactor" {
          tags "FixedClusterRuntime"
        }
        repairScheduler = component "ClusterRepairScheduler" "Bounded process-local dispatcher. It periodically and on message-free wake queries committed READY, due RETRY_WAIT, and expired CLAIMED jobs targeted at the local UUID, processes at most 16 sequentially, and owns no durable queue or repair truth. ClusterNodeRuntime starts and stops it; restart and leader changes are reconciled through later committed-state scans, not leader-owned callbacks or state-machine side effects." "Java 21 ScheduledExecutorService, Reactor" {
          tags "FixedClusterRuntime"
        }
        repairWorker = component "ClusterRepairWorker" "Fenced executor. It claims with the stable node UUID and one process-session UUID, rechecks the exact committed generation, probes the target, tries different named replicas, receives bounded direct gRPC frames into claim-token staging, verifies and durably publishes exact bytes, then proposes idempotent success, retry, blocked, or obsolete state. A later claim treats an already-valid target as success; stale tokens cannot change consensus state." "Java 21, Reactor" {
          tags "FixedClusterRuntime"
        }

        futureClusteredObjectSemantics = component "Excluded clustered object semantics" "Outside the fixed-cluster functional scope: clustered multipart state and part transfer, conditional writes, S3 versioning, and chunked-object transfer. Single-node behavior does not provide these clustered semantics." "Excluded cluster application scope" {
          tags "ExcludedScope"
        }
        futureErasureCodedTransfer = component "Excluded erasure-coded transfer" "Outside the fixed whole-object replication scope: data/parity shard encoding and transfer, policy-specific acknowledgement thresholds, and shard reconstruction. The N=3/W=2 data path replicates whole objects and does not perform erasure coding." "Excluded cluster data-plane scope" {
          tags "ExcludedScope"
        }
        futureMembershipLifecycle = component "Excluded dynamic membership lifecycle" "Outside static A/B/C bootstrap: consensus-controlled admission, catch-up, promotion, demotion, replacement, removal, fencing, certificate lifecycle, and rolling compatibility. The fixed manifest has no membership-mutation operation." "Excluded control-plane scope" {
          tags "ExcludedScope"
        }
        deferredRepairAdjacentOperations = component "Excluded repair-adjacent operations" "Outside bounded current-generation repair: prepared-artifact intents, automated orphan cleanup, rebalance execution, and broad or periodic anti-entropy discovery. The scheduler reconciles only committed local-target repair jobs and does not discover unrelated placement drift." "Excluded cluster operations scope" {
          tags "ExcludedScope"
        }
        futurePartitionResilience = component "Excluded broader partition handling" "Outside the one-node-stop flow: asymmetric partitions, delayed or reordered messages, split control/data paths, stale leaders, duplicate identities, and repeated coordinator changes. The runtime flow assumes the surviving peers remain mutually reachable." "Excluded cluster resilience scope" {
          tags "ExcludedScope"
        }
      }
    }

    deploymentEnvironment "Fixed Three-Node Runtime" {
      deploymentNode "Independent failure domains" "Nodes A, B, and C occupy independent failure domains. Bounded current-generation repair adds no process or infrastructure node: each JVM co-locates its SmartLifecycle composition, process-local repair scheduler, and fenced worker with its Ratis voter and replica data server. Consensus owns durable job state; direct mTLS gRPC and token-specific filesystem staging own whole-object side effects. The topology excludes dynamic membership and production certificate lifecycle." "Deployment infrastructure" {
        tags "FixedClusterRuntime"
        deploymentNode "Node A JVM" "One fixed JVM hosts the S3 composition, ClusterNodeRuntime lifecycle, one Ratis voter, one replica server/client set, and one local-target repair scheduler/worker. The scheduler starts after control and data services and stops before them; no process-local queue survives restart or overrides committed repair state." "Java 21 JVM" {
          tags "FixedClusterRuntime"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Node B JVM" "One fixed JVM and the missing/corrupt repair target in the repair runtime flows. Its scheduler/worker use committed claims and a process-session UUID; a different named replica transfers directly into token-specific staging, and verified publication remains separate from Ratis job completion." "Java 21 JVM" {
          tags "FixedClusterRuntime"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Node C JVM" "One fixed JVM and the repair source for node B in the repair runtime flows. Its replica server streams current-reference bytes over the direct mTLS data path; its co-located scheduler only claims jobs targeted at C, preserving local lifecycle ownership and consensus authority." "Java 21 JVM" {
          tags "FixedClusterRuntime"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
      }
    }

    deploymentEnvironment "Fixed Three-Node Acceptance" {
      deploymentNode "Isolated acceptance environment" "Fixed A/B/C run as real child JVM processes with separate roots, ephemeral test-local CA material, per-node certificates, and test-only trust stores. The fixtures exercise mTLS locally and exclude production certificate enrollment, custody, rotation, revocation, and expiry monitoring." "Isolated real-process test environment" {
        tags "FixedClusterRuntime"
        deploymentNode "Acceptance node A" "Initial S3 coordinator and co-located voter/storage node; stopped after successful CreateBucket and whole-object PUT." "Java 21 child JVM" {
          tags "FixedClusterRuntime"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Acceptance node B" "Surviving S3 endpoint and co-located voter/storage node used for exact-byte GET and post-restart recovery." "Java 21 child JVM" {
          tags "FixedClusterRuntime"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Acceptance node C" "Surviving co-located voter/storage node that retains control quorum with B and recovers persisted state after complete restart." "Java 21 child JVM" {
          tags "FixedClusterRuntime"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
      }
    }

    administrator -> magrathea "Uses admin UI, generated documentation, and management API" "HTTP/JSON"
    administrator -> magrathea.bootstrapApplication.adminServerConfig "Loads bundled admin UI and generated documentation on the admin port" "HTTP"
    administrator -> magrathea.browserFrontend.productApplication "Uses the Object Storage administration experience" "Browser interaction"
    administrator -> magrathea.adminApiAdapter.adminRouter "Calls admin management endpoints" "HTTP/JSON"

    magrathea.bootstrapApplication.magratheaApplication -> magrathea.s3ReactiveApiAdapter.s3ProxyRouter "Bootstraps S3 API routes on the main WebFlux server" "Spring Boot auto-configuration"
    magrathea.bootstrapApplication.magratheaApplication -> magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle "Creates the fixed-node SmartLifecycle composition only for the storage-engine plus cluster profiles" "Spring bean lifecycle"
    magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle -> magrathea.clusterNode.clusterControlRatisInfrastructure "Starts and stops one embedded voter and its persisted control state" "SmartLifecycle"
    magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle -> magrathea.clusterNode.clusterDataGrpcInfrastructure "Starts the replica server, owns peer clients, and closes data transport after repair scheduling stops" "SmartLifecycle"
    magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle -> magrathea.clusterNode.repairCoordinator "Assembles the request-facing repair use case with one process-session context and process-local metrics" "In-process composition"
    magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle -> magrathea.clusterNode.repairScheduler "Starts scheduling after voter and data server readiness and closes it before transport shutdown" "SmartLifecycle"
    magrathea.bootstrapApplication.magratheaApplication -> magrathea.reactiveObjectStore.reactiveObjectService "Component-scans object-store services" "Spring component scan"
    magrathea.bootstrapApplication.magratheaApplication -> magrathea.reactiveBucketManagement.reactiveBucketService "Component-scans bucket-management services" "Spring component scan"
    magrathea.bootstrapApplication.adminServerConfig -> magrathea.adminApiAdapter.adminRouter "Mounts admin API routes on the admin server" "RouterFunction"
    magrathea.bootstrapApplication.adminServerConfig -> magrathea.bootstrapApplication.staticResources "Serves copied UI, documentation, and report assets from the bootstrap classpath" "ClassPathResource"
    magrathea.bootstrapApplication.staticResources -> magrathea.browserFrontend.productApplication "Delivers the built Object Storage Admin distribution and bundled documentation" "HTTP static assets"
    magrathea.bootstrapApplication.backendSelection -> magrathea.reactiveInfrastructure "Selects in-memory repositories for the single-node profile" "Spring profiles/properties"
    magrathea.bootstrapApplication.backendSelection -> magrathea.storageEngine.storageEngineRepositoryAdapter "Selects Storage Engine-backed repositories for the storage-engine profile" "Spring profiles/properties"
    magrathea.adminApiAdapter.adminRouter -> magrathea.storageEngine.storageEngineDomain "Uses storage policy value objects for admin request mapping" "Java calls"
    magrathea.adminApiAdapter.adminRouter -> magrathea.storageEngine.storageEngineReactiveRepositoryApplication "Reads configured policy, device, disk-set, and bucket-capacity ports; reports unavailable when required ports are absent" "Reactive port interfaces"

    magrathea.browserFrontend.productApplication -> magrathea.browserFrontend.productShell "Composes the accessible product frame and extension contracts" "TypeScript/Vue imports"
    magrathea.browserFrontend.productApplication -> magrathea.browserFrontend.objectStorageExtension "Registers Object Storage routes, navigation, localization, and screens" "ProductExtension contract"
    magrathea.browserFrontend.productApplication -> magrathea.browserFrontend.browserPlatformAdapters "Uses application-owned browser effects and runtime configuration" "TypeScript calls"
    magrathea.browserFrontend.productApplication -> magrathea.browserFrontend.documentationUi "Registers bundled documentation routes" "Vue Router"
    magrathea.browserFrontend.objectStorageExtension -> magrathea.browserFrontend.productShell "Uses ProductExtension contracts and shell presentation primitives" "TypeScript/Vue imports"
    magrathea.browserFrontend.objectStorageExtension -> magrathea.browserFrontend.adminApiClient "Uses the injected Admin Control Plane port" "Typed client interface"
    magrathea.browserFrontend.objectStorageExtension -> magrathea.browserFrontend.s3HeadObjectClient "Uses the optional injected S3 diagnostic port" "Typed client interface"
    magrathea.browserFrontend.browserPlatformAdapters -> magrathea.browserFrontend.adminApiClient "Configures endpoint, connectivity, and timeout behavior" "Dependency injection"
    magrathea.browserFrontend.browserPlatformAdapters -> magrathea.browserFrontend.s3HeadObjectClient "Configures the separate endpoint and in-memory signer only when supplied" "Dependency injection"
    magrathea.browserFrontend.adminApiClient -> magrathea.adminApiAdapter.adminRouter "Calls Admin Control Plane routes" "HTTP/JSON"
    magrathea.browserFrontend.s3HeadObjectClient -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "Optionally sends a signed HeadObject diagnostic to the separately configured S3 Data Plane" "HTTP HEAD"

    user -> magrathea.s3ReactiveApiAdapter.bucketOperationsHandler "Sends S3 bucket operations requests" "HTTP"
    user -> magrathea.s3ReactiveApiAdapter.bucketMetadataHandler "Sends S3 bucket metadata requests" "HTTP"
    user -> magrathea.s3ReactiveApiAdapter.bucketConfigHandler "Sends S3 bucket configuration requests" "HTTP"
    user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "Sends S3 object operations requests" "HTTP"
    user -> magrathea.s3ReactiveApiAdapter.objectMetadataHandler "Sends S3 object metadata requests" "HTTP"
    user -> magrathea.s3ReactiveApiAdapter.multipartHandler "Sends S3 multipart upload requests" "HTTP"
    user -> magrathea.s3ReactiveApiAdapter.sessionHandler "Sends S3 session management requests" "HTTP"

    magrathea.s3ReactiveApiAdapter.bucketOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "Calls bucket lifecycle, listing, directory-bucket use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.bucketOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "Calls object listing use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.bucketMetadataHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "Calls bucket metadata use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.bucketConfigHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "Calls bucket CORS/lifecycle/policy/encryption/logging/website/notification/replication/request-payment/ownership-controls/public-access-block/accelerate/analytics/inventory/metrics/intelligent-tiering/ABAC/object-lock/metadata-config/table-config use cases (registry dispatch)" "Java service calls"
    magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "Checks bucket existence" "Java service calls"
    magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "Calls object CRUD/content/rename/torrent/restore/select/Object-Lambda use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.objectMetadataHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "Checks bucket existence" "Java service calls"
    magrathea.s3ReactiveApiAdapter.objectMetadataHandler -> magrathea.reactiveObjectStore.reactiveObjectService "Calls object metadata/legal-hold/retention/encryption-update use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.multipartHandler -> magrathea.reactiveObjectStore.reactiveMultipartUploadService "Calls multipart upload use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.sessionHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "Calls session management use cases" "Java service calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.bucketOperationsHandler "Dispatches bucket lifecycle routes" "Java calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.bucketMetadataHandler "Dispatches bucket metadata routes" "Java calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.bucketConfigHandler "Dispatches bucket config routes" "Java calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "Dispatches object operation routes" "Java calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.objectMetadataHandler "Dispatches object metadata routes" "Java calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.multipartHandler "Dispatches multipart routes" "Java calls"
    magrathea.s3ReactiveApiAdapter.s3ProxyRouter -> magrathea.s3ReactiveApiAdapter.sessionHandler "Dispatches session routes" "Java calls"

    magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "Persists and loads objects/content via domain repository port" "Repository interfaces"
    magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3Object "Creates/restores object metadata with legal hold, retention, encryption transitions" "Java calls"
    magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.contentBoundary "Wraps upload/download content" "Java calls"
    magrathea.reactiveObjectStore.reactiveMultipartUploadService -> magrathea.reactiveObjectStore.multipartUploadRepositoryPort "Persists and loads multipart uploads via domain repository port" "Repository interfaces"
    magrathea.reactiveObjectStore.reactiveMultipartUploadService -> magrathea.reactiveObjectStore.multipartUpload "Creates/updates multipart upload state with domain events" "Java calls"

    magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "Persists and loads bucket aggregates via domain repository port" "Repository interfaces"
    magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucket "Creates/updates bucket aggregate root with dedicated with* config methods (emits specific ObjectStoreEvent subtypes)" "Java calls"

    magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository "CQS command interface for S3Object persistence" "Implemented by"
    magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "CQS query interface for S3Object reads" "Implemented by"
    magrathea.reactiveObjectStore.multipartUploadRepositoryPort -> magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository "CQS command interface for MultipartUpload persistence" "Implemented by"
    magrathea.reactiveObjectStore.multipartUploadRepositoryPort -> magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository "CQS query interface for MultipartUpload reads" "Implemented by"

    magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketCommandRepository "CQS command interface for Bucket persistence" "Implemented by"
    magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketQueryRepository "CQS query interface for Bucket reads" "Implemented by"

    magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "Implemented by" "Implements"
    magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "Implemented by" "Implements"
    magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "Implemented by" "Implements"
    magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "Implemented by" "Implements"
    magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveMultipartUploadRepository "Implemented by" "Implements"
    magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveMultipartUploadRepository "Implemented by" "Implements"

    magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "Implemented by (storage-engine profile)" "Implements"
    magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "Implemented by (storage-engine profile)" "Implements"
    magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "Implemented by (storage-engine profile)" "Implements"
    magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "Implemented by (storage-engine profile)" "Implements"
    magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "Implemented by (storage-engine profile)" "Implements"
    magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "Implemented by (storage-engine profile)" "Implements"

    magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.storageEngine.storageEngineReactiveApplication "Delegates persistence to Storage Engine orchestrator" "Java calls"
    magrathea.storageEngine.storageEngineReactiveApplication -> magrathea.storageEngine.storageEngineDomain "Uses domain services (PersistencePlanner, EffectivePolicyResolver, VirtualDeviceResolver, CompleteUploadService)" "Java calls"
    magrathea.storageEngine.storageEngineReactiveApplication -> magrathea.storageEngine.storageEngineReactiveRepositoryApplication "Uses ports and catalogs (ChunkStorePort, ContentAddressIndex, ObjectManifestRepository, StoredObjectRepository, StoragePolicyCatalog, StorageDeviceCatalog, DiskSetCatalog)" "Repository interfaces"
    magrathea.storageEngine.storageEngineReactiveRepositoryApplication -> magrathea.storageEngine.storageEngineReactiveInfrastructure "Implemented by filesystem and YAML adapters" "Implements"

    magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "For the cluster profile, delegates unconditional CreateBucket and whole-object PUT/GET through transport-neutral cluster ports; remains the sole S3-to-Storage-Engine ACL" "In-process Java calls"
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.storageEngine.storageEngineDomain "Invokes pure storage placement for fixed N=3/W=2 without adding transport or lifecycle types to storage-engine-domain" "In-process Java calls"
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "Resolves and commits bucket/object-reference generations through the fixed three-voter control group" "Transport-neutral control ports"
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "Stages, sends, or reads immutable whole-object artifacts and requires checksum-valid durable acknowledgements" "Transport-neutral replica ports"
    magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterProtocol "Uses versioned replica transfer contracts with bounded manual flow control" "Generated protobuf/gRPC contracts"
    magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "A/B/C voters commit bucket and object-reference generations; peers require stable-UUID-bound mTLS" "Ratis gRPC/HTTP2 with mTLS"
    magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "Selected A/B/C storage roles transfer whole-object replicas directly and return durable checksum-valid acknowledgements; peers require stable-UUID-bound mTLS" "Application gRPC/HTTP2 streaming with mTLS"

    magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.repairCoordinator "For a cluster-profile GET, maps known local absence or single-pass integrity failure to bounded current-generation repair without exposing another object API" "In-process Java calls"
    magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.clusterControlRatisInfrastructure "Ensures or re-evaluates one canonical consensus-owned job for the exact current reference target" "Transport-neutral repair control port"
    magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.repairScheduler "Invokes exact missing-local work inline before response, or emits a message-free wake after corrupt-stream failure" "In-process Reactor calls"
    magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.clusterControlRatisInfrastructure "Queries committed locally targeted READY, due RETRY_WAIT, and expired CLAIMED work; carries no durable queue" "Transport-neutral repair query port"
    magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.repairWorker "Dispatches one exact committed job at a time, bounded to 16 jobs per scan" "In-process Reactor calls"
    magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "Claims with process-session and claim-generation fencing, rechecks current references, and proposes idempotent success, retry, blocked, or obsolete results" "Transport-neutral control ports"
    magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterDataGrpcInfrastructure "Probes, directly fetches, incrementally verifies, fsyncs, atomically replaces, and strictly fsyncs the target directory under claim-token staging" "Transport-neutral replica and local-artifact ports"

    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.futureClusteredObjectSemantics "Outside the fixed-cluster scope: clustered multipart, conditional/versioned writes, and chunked-object transfer" "Excluded application scope" {
      tags "ExcludedScope"
    }
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.futureErasureCodedTransfer "Outside whole-object replication scope: policy-specific shard placement, transfer, acknowledgement, and reconstruction" "Excluded data-plane scope" {
      tags "ExcludedScope"
    }
    magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.futureMembershipLifecycle "Outside static bootstrap scope: membership and certificate lifecycle transitions" "Excluded control-plane scope" {
      tags "ExcludedScope"
    }
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.deferredRepairAdjacentOperations "Outside bounded repair scope: prepared-artifact intents, automated orphan cleanup, rebalance, and broad or periodic anti-entropy discovery" "Excluded operations scope" {
      tags "ExcludedScope"
    }
    magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.futurePartitionResilience "Outside fixed-node control scope: asymmetric partitions, message faults, stale leaders, duplicate identities, and repeated coordinator changes" "Excluded resilience scope" {
      tags "ExcludedScope"
    }
    magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.futurePartitionResilience "Outside fixed-node data-path scope: asymmetric partitions and delayed, reordered, or split-path messages" "Excluded resilience scope" {
      tags "ExcludedScope"
    }
  }

  views {
    systemContext magrathea "SystemContext" {
      title "C1 System Context: Magrathea ObjectStore"
      description "External S3-compatible clients interact with Magrathea ObjectStore as a single software system."
      include user
      include administrator
      include magrathea
      autolayout lr
    }

    container magrathea "Container" {
      title "C2 Container: Magrathea ObjectStore"
      description "bootstrap-application assembles the S3 and Admin runtimes, the browser uses only those interfaces, and storage-engine remains behind the Object Store ACL. The Cluster Node Runtime uses fixed co-located A/B/C JVMs for unconditional CreateBucket, single-part whole-object PUT/GET, and bounded repair of a missing or corrupt replica named by the current committed generation. Ratis owns repair identity, lifecycle, claims, fencing, results, and snapshot v1-to-v2 recovery; direct mTLS gRPC and token-specific filesystem staging own verified bytes. A missing-local GET repairs before response streaming; corruption found during the single local response pass fails that request and can benefit only a later GET after repair. The cluster scope excludes dynamic membership, clustered multipart/versioning, erasure coding, rebalance, broad or periodic anti-entropy discovery, and automated orphan cleanup."
      include user
      include administrator
      include magrathea.bootstrapApplication
      include magrathea.browserFrontend
      include magrathea.adminApiAdapter
      include magrathea.s3ReactiveApiAdapter
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include magrathea.reactiveInfrastructure
      include magrathea.storageEngine
      include magrathea.clusterNode
      autolayout lr
    }

    component magrathea.bootstrapApplication "BootstrapApplicationComponents" {
      title "C3 Component: bootstrap-application"
      description "Executable application assembly and runtime composition boundary. It starts the S3 WebFlux application, starts the admin/static Netty server, selects the object-store backend, and serves static UI/docs/report assets copied into bootstrap-application during generate-resources. bootstrap-application, rather than admin-api-adapter, owns the static assets."
      include administrator
      include user
      include *
      include magrathea.adminApiAdapter
      include magrathea.s3ReactiveApiAdapter
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveInfrastructure
      include magrathea.storageEngine
      autolayout lr
    }

    component magrathea.browserFrontend "ObjectStorageAdminBrowserComponents" {
      title "C3 Component: Object Storage Admin Browser Application"
      description "The deployable application composes the product-neutral Product Shell and independently maintained Object Storage Product Extension, owns browser/platform and documentation integration, and injects separate typed Admin API and optional S3 HeadObject clients. Operational report screens preserve provider-not-configured responses; report, audit, metrics, trace, recovery, garbage-collection, and scrub providers are outside this browser component boundary."
      include administrator
      include magrathea.bootstrapApplication
      include *
      include magrathea.adminApiAdapter
      include magrathea.s3ReactiveApiAdapter
      autolayout lr
    }

    component magrathea.adminApiAdapter "AdminApiAdapterComponents" {
      title "C3 Component: admin-api-adapter"
      description "JSON Admin Control Plane routes consumed by the browser application and mounted by the bootstrap admin server. Catalog and capacity behavior is backed by configured ports; operational reports return provider-not-configured when no matching provider is configured. Frontend and generated documentation assets are handed off to bootstrap-application static resources."
      include administrator
      include magrathea.bootstrapApplication
      include *
      include magrathea.storageEngine
      autolayout lr
    }

    component magrathea.s3ReactiveApiAdapter "S3ReactiveApiAdapterComponents" {
      title "C3 Component: s3-reactive-api-adapter"
      description "RouterFunction and handler components that expose the S3-compatible HTTP protocol. Includes S3ProxyRouter, S3SessionHandler, S3WebSupport, and all six handler components with registry dispatch in S3BucketConfigHandler."
      include user
      include *
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      autolayout lr
    }

    component magrathea.reactiveObjectStore "ReactiveObjectStoreComponents" {
      title "C3 Component: reactive-object-store"
      description "Reactive object and multipart upload application services, domain aggregates with state transitions and domain events, repository ports, and content boundary. The functional scope includes legal hold, retention, torrent, restore, select, Object Lambda, and encryption update."
      include magrathea.s3ReactiveApiAdapter
      include *
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include magrathea.reactiveInfrastructure
      autolayout lr
    }

    component magrathea.reactiveBucketManagement "ReactiveBucketManagementComponents" {
      title "C3 Component: reactive-bucket-management"
      description "Reactive bucket management service, bucket aggregate root with dedicated with* methods per configuration type and specific ObjectStoreEvent subtypes, and repository port. The functional scope includes ABAC, object lock, metadata configuration, and table configuration."
      include magrathea.s3ReactiveApiAdapter
      include magrathea.reactiveObjectStore
      include *
      include magrathea.reactiveRepositoryApplication
      include magrathea.reactiveInfrastructure
      autolayout lr
    }

    component magrathea.reactiveRepositoryApplication "ReactiveRepositoryApplicationComponents" {
      title "C3 Component: reactive-repository-application"
      description "Command/Query repository interfaces for bucket, S3Object and multipart upload aggregates."
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include *
      include magrathea.reactiveInfrastructure
      include magrathea.storageEngine
      autolayout lr
    }

    component magrathea.reactiveInfrastructure "ReactiveInfrastructureComponents" {
      title "C3 Component: reactive-infrastructure"
      description "Reactive repository adapters used by the application capabilities. They store session, ABAC, object lock, metadata/table configuration, legal hold, retention, restore, and select data. Internal ConcurrentHashMap structures are intentionally not shown as C4 components."
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include *
      autolayout lr
    }

    component magrathea.storageEngine "StorageEngineComponents" {
      title "C3 Component: storage-engine"
      description "Storage Engine bounded context: domain model, reactive repository/application ports, reactive orchestration, filesystem/YAML adapters, and the Anti-Corruption Layer that remains the only S3 integration boundary. Under the cluster profile, its repository adapter invokes bounded current-generation repair through transport-neutral cluster application ports. Pure placement planning does not become a durable scheduler, broad anti-entropy executor, rebalance engine, or orphan cleaner."
      include *
      include magrathea.clusterNode
      autolayout lr
    }

    component magrathea.clusterNode "ClusterNodeComponents" {
      title "C3 Component: Bounded Fixed-Cluster Node Runtime"
      description "The fixed A/B/C runtime coordinates unconditional CreateBucket, single-part whole-object PUT/GET, and consensus-owned current-generation repair. ClusterNodeRuntime owns process lifecycle; Ratis owns durable work and fencing; the scheduler only rediscovers committed local-target jobs; and the worker owns verified direct side effects. Dynamic membership, clustered multipart/versioning, erasure coding, rebalance, broad or periodic anti-entropy discovery, and automated orphan cleanup are outside this component scope."
      include magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle
      include magrathea.storageEngine.storageEngineRepositoryAdapter
      include magrathea.storageEngine.storageEngineDomain
      include magrathea.clusterNode.storageEngineClusterApplication
      include magrathea.clusterNode.clusterProtocol
      include magrathea.clusterNode.clusterControlRatisInfrastructure
      include magrathea.clusterNode.clusterDataGrpcInfrastructure
      include magrathea.clusterNode.repairCoordinator
      include magrathea.clusterNode.repairScheduler
      include magrathea.clusterNode.repairWorker
      autolayout lr
    }

    component magrathea.clusterNode "RepairComponents" {
      title "C3 Component: Bounded Consensus-Owned Current-Generation Repair"
      description "The Storage Engine ACL distinguishes known local absence before response streaming from corruption detected during the single local response pass. ClusterRepairCoordinator commits canonical current-generation work; ClusterRepairScheduler is a lifecycle-owned bounded dispatcher with no durable queue; and ClusterRepairWorker executes fenced claims through Ratis control and direct replica/filesystem ports. ClusterNodeRuntime starts scheduling after control and data services and stops it before transport shutdown. The repair scope excludes broad or periodic anti-entropy discovery, rebalance execution, and automated orphan cleanup."
      include magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle
      include magrathea.storageEngine.storageEngineRepositoryAdapter
      include magrathea.clusterNode.repairCoordinator
      include magrathea.clusterNode.repairScheduler
      include magrathea.clusterNode.repairWorker
      include magrathea.clusterNode.clusterControlRatisInfrastructure
      include magrathea.clusterNode.clusterDataGrpcInfrastructure
      include magrathea.clusterNode.clusterProtocol
      autolayout lr
    }

    component magrathea.clusterNode "FutureClusterCapabilities" {
      title "C3 Component: Excluded Cluster Capabilities"
      description "The fixed A/B/C cluster scope excludes clustered multipart and conditional/versioned writes, erasure-coded transfer/reconstruction, dynamic membership and certificate lifecycle, prepared-artifact intents, automated orphan cleanup, rebalance, broad or periodic anti-entropy discovery, and broader partition behavior. Single-node features and pure placement planning do not supply these cluster behaviors."
      include magrathea.clusterNode.storageEngineClusterApplication
      include magrathea.clusterNode.clusterControlRatisInfrastructure
      include magrathea.clusterNode.clusterDataGrpcInfrastructure
      include magrathea.clusterNode.futureClusteredObjectSemantics
      include magrathea.clusterNode.futureErasureCodedTransfer
      include magrathea.clusterNode.futureMembershipLifecycle
      include magrathea.clusterNode.deferredRepairAdjacentOperations
      include magrathea.clusterNode.futurePartitionResilience
      autolayout lr
    }

    dynamic magrathea.browserFrontend "AdminStaticAssetRuntime" {
      title "Runtime: Object Storage Administration"
      description "The admin server delivers the independently built browser distribution and bundled documentation. The running browser application composes Product Shell with the Object Storage Product Extension and calls Admin Control Plane routes. Operational report routes return provider-not-configured when no provider exists; S3 HeadObject is attempted only when its separate endpoint and signer are configured."
      administrator -> magrathea.bootstrapApplication.adminServerConfig "1. GET /, /assets/**, or /docs/**" "HTTP"
      magrathea.bootstrapApplication.adminServerConfig -> magrathea.bootstrapApplication.staticResources "2. Resolve classpath static resource" "ClassPathResource"
      magrathea.bootstrapApplication.staticResources -> magrathea.browserFrontend.productApplication "3. Deliver application and documentation assets" "HTTP static assets"
      administrator -> magrathea.browserFrontend.productApplication "4. Navigate an administration screen" "Browser interaction"
      magrathea.browserFrontend.productApplication -> magrathea.browserFrontend.objectStorageExtension "5. Render the registered product route" "ProductExtension contract"
      magrathea.browserFrontend.objectStorageExtension -> magrathea.browserFrontend.adminApiClient "6. Request Admin Control Plane data" "Typed client interface"
      magrathea.browserFrontend.adminApiClient -> magrathea.adminApiAdapter.adminRouter "7. GET/POST/PUT /admin/**" "HTTP/JSON"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "ClusterCreateBucketRuntime" {
      title "Runtime: Bounded Fixed-Cluster CreateBucket"
      description "The S3 and Object Store boundaries submit an unconditional CreateBucket through the Storage Engine ACL. The fixed A/B/C voter group commits the new bucket generation before S3 success. Dynamic membership and broader bucket configuration are outside this runtime flow."
      user -> magrathea.s3ReactiveApiAdapter.bucketOperationsHandler "1. Submit unconditional S3 CreateBucket through node A" "HTTP"
      magrathea.s3ReactiveApiAdapter.bucketOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. Invoke existing bucket use case" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "3. Persist through the existing repository boundary" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketCommandRepository "4. Select the cluster-profile repository" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "5. Enter the existing Storage Engine ACL" "Implements"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Submit the transport-neutral CreateBucket command" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "7. Commit the next bucket generation through A/B/C quorum" "Transport-neutral control ports"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "8. Replicate and commit the generation before success" "Ratis gRPC/HTTP2 with mTLS"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "ClusterWriteRuntime" {
      title "Runtime: Bounded Fixed-Cluster Whole-Object Write"
      description "Node A coordinates one unconditional, single-part whole-object PUT. Pure storage placement selects fixed nodes A/B/C for N=3; direct replica transfer must obtain W=2 checksum-valid durable acknowledgements, then the A/B/C control quorum commits the object-reference generation before S3 success. There is no degraded write: fewer than two data acknowledgements or loss of control quorum fails publication."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. Submit unconditional whole-object S3 PutObject through node A" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "2. Invoke existing object write use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "3. Persist through the existing repository boundary" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository "4. Select the cluster-profile repository" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "5. Enter the existing Storage Engine ACL" "Implements"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Coordinate the transport-neutral whole-object write" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.storageEngine.storageEngineDomain "7. Use pure storage placement to select A/B/C at N=3/W=2" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "8. Stage and directly transfer the immutable whole-object artifact" "Transport-neutral replica ports"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "9. Return identity-bound checksum-valid durable acknowledgements from selected nodes" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "10. Only after W=2, submit the verified object-reference generation" "Transport-neutral control ports"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "11. Commit the reference through A/B/C quorum before S3 success" "Ratis gRPC/HTTP2 with mTLS"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "ClusterFailoverReadRuntime" {
      title "Runtime: Exact-Byte GET After Coordinator A Stops"
      description "After a successful whole-object PUT, coordinator A is stopped. B and C retain the two-voter control quorum. A client uses node B's S3 endpoint; B resolves the committed object-reference generation and returns bytes from a referenced checksum-valid replica. This flow covers one stopped coordinator while B and C remain mutually reachable; it excludes dynamic membership, rebalance, broader partition faults, and rolling upgrades."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. After stopping A, submit S3 GetObject through node B" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "2. Invoke existing object read use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "3. Read through the existing repository boundary" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "4. Select the cluster-profile repository" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "5. Enter the existing Storage Engine ACL on B" "Implements"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Coordinate the transport-neutral whole-object read" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "7. B resolves the committed reference while B+C retain quorum" "Transport-neutral control ports"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "8. B+C serve the authoritative committed generation; A is unavailable" "Ratis gRPC/HTTP2 with mTLS"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "9. Read a referenced whole-object replica and validate checksum and length" "Transport-neutral replica ports"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "10. Stream exact bytes to B from a surviving referenced replica when needed" "Application gRPC/HTTP2 streaming with mTLS"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "ClusterCompleteRestartRuntime" {
      title "Runtime: Complete A/B/C Restart Recovery"
      description "All three JVMs stop and discard process memory, then restart from their original non-empty roots. Each node recovers its stable UUID, Ratis state, immutable replicas, and committed bucket/object-reference generations. Reordered seeds do not rewrite persisted membership. An exact-byte GET proceeds through B after recovery; rolling upgrades and membership changes are outside this runtime flow."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. Before restart, address a consensus-committed whole object through S3" "HTTP"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "2. Stop all A/B/C voters after persisted log and snapshots" "Ratis gRPC/HTTP2 with mTLS"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "3. Stop all A/B/C replica servers with published artifacts on independent roots" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "4. Restart each voter from its stable UUID and non-empty Ratis root" "Ratis gRPC/HTTP2 with mTLS"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "5. Restart each replica server from its object root" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Node B requests the committed object through the cluster profile" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "7. Resolve recovered bucket and object-reference generations" "Transport-neutral control ports"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "8. Validate length and checksum and return exact bytes" "Transport-neutral replica ports"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "MissingReplicaRepairRuntime" {
      title "Runtime: Bounded Missing-Local Repair Before GET Response"
      description "Known local absence is established before response payload starts. The request path commits or deduplicates the exact current-generation repair job, invokes a fenced claim inline, completely verifies a direct named-replica transfer, and durably publishes it before opening the repaired local file once for the response. There is no complete local-payload preflight followed by a second filesystem read."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. Submit S3 GetObject to fixed target node B" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "2. Invoke the existing object read use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "3. Read through the existing repository boundary" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "4. Select the cluster-profile repository" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "5. Enter the existing Storage Engine ACL" "Implements"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Resolve the current committed whole-object reference and exact artifact facts" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "7. Read generation, artifact, target, length, SHA-256, topology, policy, and named replicas" "Transport-neutral control ports"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "8. Establish that B's promised local artifact is absent before response streaming" "Transport-neutral replica ports"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.repairCoordinator "9. Ensure the canonical repair identity and require inline durable repair" "In-process Java calls"
      magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.clusterControlRatisInfrastructure "10. Commit or deduplicate READY work for the exact current reference target" "Transport-neutral repair control port"
      magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.repairScheduler "11. Invoke repairNow for that exact committed job; do not wait for a background scan" "In-process Reactor calls"
      magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.repairWorker "12. Delegate the exact job to the fenced worker" "In-process Reactor calls"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "13. Commit a process-session and claim-generation fence, then recheck currentness" "Transport-neutral control ports"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterDataGrpcInfrastructure "14. Receive C's bounded frames into claim-token staging, verify all bytes, fsync, atomically replace, and fsync B's directory" "Transport-neutral replica and local-artifact ports"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "15. Stream from different named source C directly to target B; payload bytes never enter Ratis" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "16. Recheck currentness and commit SUCCEEDED only after exact durable publication" "Transport-neutral control ports"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "17. Open B's repaired artifact once and stream it while calculating length and SHA-256" "Transport-neutral replica ports"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "CorruptReplicaRepairRuntime" {
      title "Runtime: Single-Pass Corruption Fails Now and Repairs a Later GET"
      description "A present local artifact is opened once and streamed while length and SHA-256 are calculated incrementally; the reader retains at most the pending final frame, not a complete preflight copy. Corruption discovered in that pass fails the current GET and is never transparently retried from another replica because response bytes may already be committed. Durable ensure completes before repair is reported as scheduled; a message-free wake lets the lifecycle-owned scheduler rediscover committed work, and only a later GET may benefit."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. Submit S3 GetObject to fixed target node B" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "2. Invoke the existing object read use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "3. Read through the existing repository boundary" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "4. Select the cluster-profile repository" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "5. Enter the existing Storage Engine ACL" "Implements"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Resolve the current committed whole-object reference and exact artifact facts" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "7. Read generation, artifact, target, length, SHA-256, topology, policy, and named replicas" "Transport-neutral control ports"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "8. Open B's present file once; emit bounded prior frames while retaining only the pending final frame and checking all bytes" "Transport-neutral replica ports"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.repairCoordinator "9. On length or SHA-256 mismatch, preserve the integrity failure and ensure durable repair work" "In-process Java calls"
      magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.clusterControlRatisInfrastructure "10. Commit or deduplicate the canonical current-generation job before reporting it scheduled" "Transport-neutral repair control port"
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "11. Observe this GET as failed; do not transparently retry source C after the response pass began" "HTTP"
      magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.repairScheduler "12. Emit a message-free wake; committed Ratis work, not the wake, remains authoritative" "In-process Reactor calls"
      magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.clusterControlRatisInfrastructure "13. Query committed locally targeted READY work during a bounded scan" "Transport-neutral repair query port"
      magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.repairWorker "14. Dispatch the exact committed job sequentially" "In-process Reactor calls"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "15. Commit a current claim and recheck the exact reference obligation" "Transport-neutral control ports"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterDataGrpcInfrastructure "16. Probe corruption, fetch C into token staging, verify completely, fsync, atomically replace, and fsync B's directory" "Transport-neutral replica and local-artifact ports"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "17. Stream exact bytes from different named source C directly to B; payload bytes never enter Ratis" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "18. Commit SUCCEEDED only for the current token after durable exact publication" "Transport-neutral control ports"
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "19. Submit a later GET after repair has committed" "HTTP"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "20. Open the repaired local artifact once; this later GET may now succeed" "Transport-neutral replica ports"
      autolayout lr
    }

    dynamic magrathea.clusterNode "RepairRestartRetryRuntime" {
      title "Runtime: Bounded Repair Reconciliation Across Restart or Leader Change"
      description "Snapshot version 2 and log replay preserve canonical jobs, attempt history, process sessions, claim generations, retry state, and deduplicated results; version 1 loads with no invented jobs. ClusterNodeRuntime creates a new process session and restarts the bounded scheduler after voter/data startup. A leader change has no job-bearing callback: the running 250 ms committed-state scan retries queries through the leader-mediated adapter. Expired reclaim uses a newer generation that fences the old token, and an already-valid target completes idempotently."
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.repairCoordinator "1. Ensure one canonical job for the exact current-generation obligation" "In-process Java calls"
      magrathea.clusterNode.repairCoordinator -> magrathea.clusterNode.clusterControlRatisInfrastructure "2. Commit or deduplicate durable work" "Transport-neutral repair control port"
      magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.repairWorker "3. Dispatch a committed job for claim generation g1" "In-process Reactor calls"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "4. Commit the g1 process-session claim and recheck currentness" "Transport-neutral control ports"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterDataGrpcInfrastructure "5. Perform a bounded side effect, potentially publishing exact bytes before completion is acknowledged" "Transport-neutral replica and local-artifact ports"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "6. Keep payload bytes and token staging outside Ratis snapshots and logs" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle -> magrathea.clusterNode.repairScheduler "7. On process stop, cancel the process-local scan; committed jobs remain authoritative" "SmartLifecycle"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "8. Restore snapshot v2 plus log, or elect another leader, while preserving monotonic fences" "Ratis gRPC/HTTP2 with mTLS"
      magrathea.bootstrapApplication.clusterNodeRuntimeLifecycle -> magrathea.clusterNode.repairScheduler "9. After voter and data startup, create a new process session and restart the bounded periodic scan" "SmartLifecycle"
      magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.clusterControlRatisInfrastructure "10. Query READY, due RETRY_WAIT, and expired CLAIMED work; reclaim only through generation g2" "Transport-neutral repair query port"
      magrathea.clusterNode.repairScheduler -> magrathea.clusterNode.repairWorker "11. Dispatch the current g2 claim" "In-process Reactor calls"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterDataGrpcInfrastructure "12. Probe the target and treat an already-valid exact artifact as idempotent success" "Transport-neutral replica and local-artifact ports"
      magrathea.clusterNode.repairWorker -> magrathea.clusterNode.clusterControlRatisInfrastructure "13. Commit SUCCEEDED for g2; reject any late g1 completion as stale with no state effect" "Transport-neutral control ports"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "CreateBucketRuntime" {
      title "Runtime: CreateBucket"
      description "PUT /{bucket} creates a Bucket aggregate root and persists it in reactive-infrastructure."
      user -> magrathea.s3ReactiveApiAdapter.bucketOperationsHandler "1. PUT /{bucket}" "HTTP"
      magrathea.s3ReactiveApiAdapter.bucketOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. CreateBucket use case" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "3. Persist Bucket aggregate via domain repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketCommandRepository "4. CQS command interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "5. Persist Bucket aggregate" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "PutObjectRuntime" {
      title "Runtime: PutObject"
      description "PutObject verifies the bucket, then persists object metadata and content. Two persistence implementations exist: in-memory (reactive-infrastructure) and storage-engine profile (storage-engine)."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. PUT /{bucket}/{key}" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. Verify bucket exists" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "3. Read bucket aggregate via domain repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketQueryRepository "4. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "5. Read bucket aggregate" "Implements"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "6. PutObject use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "7. Persist object metadata and content via domain repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository "8. CQS command interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "9. Persist object metadata and bytes (in-memory profile)" "Implements"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "9b. Persist object metadata and bytes (storage-engine profile)" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "GetObjectRuntime" {
      title "Runtime: GetObject"
      description "GetObject verifies the bucket, then loads object metadata and binary content. Two persistence implementations exist: in-memory (reactive-infrastructure) and storage-engine profile (storage-engine)."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. GET /{bucket}/{key}" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. Verify bucket exists" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "3. Read bucket aggregate via domain repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketQueryRepository "4. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "5. Read bucket aggregate" "Implements"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "6. GetObject use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "7. Read object metadata and content via domain repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "8. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "9. Read object metadata and bytes (in-memory profile)" "Implements"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "9b. Read object metadata and bytes (storage-engine profile)" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "MultipartUploadRuntime" {
      title "Runtime: Multipart upload lifecycle"
      description "Multipart endpoints manage upload sessions and uploaded part state. Two persistence implementations exist: in-memory (reactive-infrastructure) and storage-engine profile (storage-engine)."
      user -> magrathea.s3ReactiveApiAdapter.multipartHandler "1. POST/PUT/GET/DELETE multipart endpoints" "HTTP"
      magrathea.s3ReactiveApiAdapter.multipartHandler -> magrathea.reactiveObjectStore.reactiveMultipartUploadService "2. Multipart upload lifecycle use cases" "Java service calls"
      magrathea.reactiveObjectStore.reactiveMultipartUploadService -> magrathea.reactiveObjectStore.multipartUploadRepositoryPort "3. Persist/load multipart upload state via domain repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.multipartUploadRepositoryPort -> magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository "4. CQS command interface" "Implemented by"
      magrathea.reactiveObjectStore.multipartUploadRepositoryPort -> magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository "5. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveMultipartUploadRepository "6. Persist multipart upload state (in-memory profile)" "Implements"
      magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveMultipartUploadRepository "7. Read multipart upload state (in-memory profile)" "Implements"
      magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "6b. Persist multipart upload state (storage-engine profile)" "Implements"
      magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "7b. Read multipart upload state (storage-engine profile)" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "BucketConfigurationRuntime" {
      title "Runtime: Bucket Configuration"
      description "GET/PUT/DELETE /{bucket}?{config} dispatches to S3BucketConfigHandler which uses registry/strategy pattern to call ReactiveBucketService methods that update the Bucket aggregate root with dedicated with* config methods."
      user -> magrathea.s3ReactiveApiAdapter.bucketConfigHandler "1. GET/PUT/DELETE /{bucket}?{config}" "HTTP"
      magrathea.s3ReactiveApiAdapter.bucketConfigHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. Dispatch to {get|put|delete}{Config} via registry strategy" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucket "3. Read/update Bucket aggregate root with dedicated with* config method" "Java calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "4. Persist Bucket aggregate via domain repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketCommandRepository "5. CQS command interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "6. Persist updated Bucket aggregate with configuration" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "PhaseFAdvancedOperationsRuntime" {
      title "Runtime: Advanced Object and Bucket Operations"
      description "Legal hold, retention, torrent, restore, select, Object Lambda, rename, encryption update, ABAC, object lock, session, and metadata/table configuration follow the RouterFunction dispatch and service delegation pattern. Two persistence implementations exist: in-memory (reactive-infrastructure) and the storage-engine profile (storage-engine)."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. Advanced object request (rename, torrent, restore, select, Object Lambda)" "HTTP"
      user -> magrathea.s3ReactiveApiAdapter.objectMetadataHandler "2. Advanced metadata request (legal hold, retention, encryption update)" "HTTP"
      user -> magrathea.s3ReactiveApiAdapter.sessionHandler "3. CreateSession request" "HTTP"
      user -> magrathea.s3ReactiveApiAdapter.bucketConfigHandler "4. Advanced config request (ABAC, object lock, metadata/table config)" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "5. Delegate object operations to ReactiveObjectService" "Java service calls"
      magrathea.s3ReactiveApiAdapter.objectMetadataHandler -> magrathea.reactiveObjectStore.reactiveObjectService "6. Delegate metadata operations to ReactiveObjectService" "Java service calls"
      magrathea.s3ReactiveApiAdapter.sessionHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "7. Delegate session creation to ReactiveBucketService" "Java service calls"
      magrathea.s3ReactiveApiAdapter.bucketConfigHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "8. Delegate advanced config to ReactiveBucketService" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "9. Persist/load object data via repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "10. Persist/load bucket data via repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository "11. CQS command interface" "Implemented by"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "12. CQS query interface" "Implemented by"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketCommandRepository "13. CQS command interface" "Implemented by"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketQueryRepository "14. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "15. Persist/load S3Object data (in-memory profile)" "Implements"
      magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "16. Persist/load bucket data (in-memory profile)" "Implements"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "15b. Persist/load S3Object data (storage-engine profile)" "Implements"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "16b. Load S3Object data (storage-engine profile)" "Implements"
      magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "17b. Persist/load bucket data (storage-engine profile)" "Implements"
      magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "18b. Load bucket data (storage-engine profile)" "Implements"
      autolayout lr
    }

    deployment magrathea "Fixed Three-Node Runtime" "FixedThreeNodeRuntimeDeployment" {
      title "Deployment: Fixed A/B/C Runtime with Bounded Repair"
      description "Each JVM co-locates ClusterNodeRuntime SmartLifecycle composition, one Ratis voter, one replica server/client set, one request-facing repair coordinator, one process-local bounded scheduler, and one fenced worker. The lifecycle starts scheduling after control and data services and stops it before transport shutdown. Ratis replicates repair metadata and state only; direct mTLS gRPC and token-specific filesystem staging carry verified whole-object bytes. The topology excludes broad anti-entropy, rebalance, automated orphan cleanup, dynamic membership, erasure coding, clustered multipart/versioning, and broader partition handling."
      include *
      autolayout tb
    }

    deployment magrathea "Fixed Three-Node Acceptance" "FixedThreeNodeAcceptanceDeployment" {
      title "Deployment: Isolated Real-Process A/B/C Acceptance Topology"
      description "Real child JVMs run isolated A/B/C roots with ephemeral test-local CA/certificate/trust fixtures. Node A is the initial S3 coordinator, node B is the surviving read and repair target, and node C is a surviving voter and named repair source. WebTestClient and AWS CLI drive the existing S3 endpoint. The ephemeral PKI is test-only; production certificate enrollment, custody, rotation, revocation, and expiry monitoring are outside this topology."
      include *
      autolayout tb
    }

    styles {
      element "Person" {
        shape person
        background #ffffff
        color #444444
      }
      element "Software System" {
        background #1168bd
        color #ffffff
      }
      element "Container" {
        background #438dd5
        color #ffffff
      }
      element "Database" {
        shape cylinder
        background #438dd5
        color #ffffff
      }
      element "Component" {
        background #85bbf0
        color #000000
      }
      element "FixedClusterRuntime" {
        background #2f855a
        color #ffffff
        stroke #22543d
      }
      element "ExcludedScope" {
        background #6b7280
        color #ffffff
        stroke #374151
      }
      relationship "ExcludedScope" {
        color #6b7280
        dashed true
      }
    }

    theme default
  }
}
