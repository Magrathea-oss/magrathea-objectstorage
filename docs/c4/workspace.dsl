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

      adminApiAdapter = container "admin-api-adapter" "Management HTTP adapter that exposes the implemented JSON Admin Control Plane for health/readiness, backend status, read-only catalogs, non-persistent policy validation, capacity/quota, and operational-report availability. Frontend/static documentation assets are handed off to bootstrap-application and served from its classpath static resources." "Spring Boot 4 WebFlux, RouterFunction" {
        adminRouter = component "AdminRouter" "Defines the /admin JSON route families. Catalog and capacity routes use configured storage-engine ports; backend status uses its configured provider; operational report routes return explicit provider-not-configured responses when no matching real provider exists." "Spring @Configuration, RouterFunction"
      }

      s3ReactiveApiAdapter = container "s3-reactive-api-adapter" "HTTP adapter that exposes the S3-compatible REST API and translates HTTP/XML/binary requests into reactive application use cases." "Spring Boot 4 WebFlux, RouterFunction, Java 21, Jackson XML" {
        bucketOperationsHandler = component "S3BucketOperationsHandler" "Handles bucket lifecycle endpoints: create, delete, head, list, location, versioning and directory-bucket listing." "Java WebFlux handler"
        bucketMetadataHandler = component "S3BucketMetadataHandler" "Handles bucket metadata endpoints such as ACL and tagging." "Java WebFlux handler"
        bucketConfigHandler = component "S3BucketConfigHandler" "Handles bucket configuration endpoints using registry/strategy dispatch pattern: CORS, lifecycle, policy, encryption, logging, website, notification, replication, request payment, ownership controls, public access block, accelerate, analytics, inventory, metrics, intelligent-tiering, ABAC, object lock, bucket metadata configuration, metadata table configuration, inventory table configuration, journal table configuration." "Java WebFlux handler, ConfigHandlerRegistry"
        objectOperationsHandler = component "S3ObjectOperationsHandler" "Handles object operations: put, get, head, delete, copy, multi-delete, rename, torrent, restore, select, and Object Lambda response." "Java WebFlux handler"
        objectMetadataHandler = component "S3ObjectMetadataHandler" "Handles object metadata endpoints such as ACL, tagging, attributes, legal hold, retention, and encryption metadata." "Java WebFlux handler"
        multipartHandler = component "S3MultipartHandler" "Handles multipart upload lifecycle: initiate, upload part, list parts, complete, abort and list uploads." "Java WebFlux handler"
        sessionHandler = component "S3SessionHandler" "Handles CreateSession for Phase F session management." "Java WebFlux handler"
        s3ProxyRouter = component "S3ProxyRouter" "Entry point RouterFunction: defines all S3 routes and dispatches requests to handlers based on HTTP method, path, query parameters, and headers." "RouterFunction"
        s3WebSupport = component "S3WebSupport" "Shared request predicates, XML parsing, error response helpers, and S3 error serialization." "Java utility"
      }

      reactiveObjectStore = container "reactive-object-store" "Reactive object storage capability: object upload, download, metadata lookup, deletion, multipart object workflows, Phase F advanced operations (legal hold, retention, torrent, restore, select, Object Lambda, encryption update) using reactive services and domain aggregates." "Java 21 reactive application/domain services" {
        reactiveObjectService = component "ReactiveObjectService" "Reactive application service for object CRUD, metadata lookup, object listing, binary content retrieval, and Phase F operations (legal hold, retention, torrent, restore, select, Object Lambda, rename, encryption update)." "Spring @Service"
        reactiveMultipartUploadService = component "ReactiveMultipartUploadService" "Reactive application service for multipart upload sessions and part lifecycle." "Spring @Service"
        s3Object = component "S3Object" "Domain aggregate for object identity, bucket id, key, ETag, storage class, content metadata, legal hold, retention, and encryption. Enforces state transitions with withLegalHold/withoutLegalHold, withRetention/withoutRetention, withEncryption/withoutEncryption. Emits ObjectStoreEvent domain events on transitions." "Domain aggregate"
        multipartUpload = component "MultipartUpload" "Domain aggregate for multipart upload state, uploaded parts and completion/abort status. Emits ObjectStoreEvent domain events on transitions." "Domain aggregate"
        s3ObjectRepositoryPort = component "S3ObjectRepository port" "Domain repository port for object metadata and binary content persistence (including legal hold, retention, restore, select data)." "Domain repository interface"
        multipartUploadRepositoryPort = component "MultipartUploadRepository port" "Domain repository port for multipart upload persistence." "Domain repository interface"
        contentBoundary = component "DefaultS3ObjectWrite / DefaultS3ObjectContent" "Application-layer content boundary for carrying Flux<DataBuffer> without leaking reactive types into the domain model." "Java adapter"
      }

      reactiveBucketManagement = container "reactive-bucket-management" "Reactive bucket management capability: bucket lifecycle, metadata, configuration, CORS/versioning, bucket-level operations, and Phase F advanced bucket config (ABAC, object lock, metadata config, table config)." "Java 21 reactive application/domain services" {
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
        inMemoryReactiveBucketRepository = component "InMemoryReactiveBucketRepository" "In-memory reactive implementation of BucketCommandRepository and BucketQueryRepository. Internally stores bucket aggregates in ConcurrentHashMap structures. Extended to support session, ABAC, object lock, and metadata/table configuration storage." "Spring @Repository"
        inMemoryReactiveS3ObjectRepository = component "InMemoryReactiveS3ObjectRepository" "In-memory reactive implementation of S3ObjectCommandRepository and S3ObjectQueryRepository. Internally stores object metadata and raw object bytes in ConcurrentHashMap structures. Extended to support legal hold, retention, restore, and select request/response storage." "Spring @Repository"
        inMemoryReactiveMultipartUploadRepository = component "InMemoryReactiveMultipartUploadRepository" "In-memory reactive implementation of MultipartUploadCommandRepository and MultipartUploadQueryRepository. Internally stores multipart upload sessions in a ConcurrentHashMap structure." "Spring @Repository"
      }

      storageEngine = container "storage-engine" "Storage Engine bounded context: persistence pipeline, virtual devices, storage policies, optional deduplication, erasure coding, content-addressed storage, and manifest management. MINIO_STANDARD is modeled as STANDARD with dedup disabled, EC enabled (4 data / 2 parity), replication factor 1, compression disabled, and encryption disabled by default." "Java 21, domain-driven" {
        storageEngineDomain = component "storage-engine-domain" "Pure domain model: StoragePolicy, EffectiveStoragePolicy, VirtualDevice, DedupNamespace, WorkflowCompatibilityKey, DeviceConfigurationHash, StepPlan, StepExecutionTrace, ChunkPersistenceTrace, ObjectManifest, StorageDevice, DiskSet, and StoredObject aggregate. Zero framework dependencies. MINIO_STANDARD semantics are STANDARD with dedup disabled, EC enabled (4 data / 2 parity), replication factor 1, compression disabled, and encryption disabled by default." "Java 21 domain"
        storageEngineReactiveRepositoryApplication = component "storage-engine-reactive-repository-application" "Reactive ports, catalogs, and repository interfaces used by the Storage Engine application: StoragePolicyCatalog, StorageDeviceCatalog, DiskSetCatalog, StoredObjectRepository, ObjectManifestRepository, ContentAddressIndex, DataTransformPort, AlterationPort, ChunkStorePort, and ECOutcome. SHA-256 checksums are computed directly inside pipeline/filesystem components without a dedicated checksum abstraction." "Java 21 CQS/port interfaces"
        storageEngineReactiveApplication = component "storage-engine-reactive-application" "Reactive orchestration and use cases: ReactiveStorageOrchestrator, Chunker, and ApplicationChunkPayload coordinate domain planning with repository/catalog/adapter ports." "Spring @Service, Reactor"
        storageEngineReactiveInfrastructure = component "storage-engine-reactive-infrastructure" "Filesystem and YAML adapters/configuration: FileSystemStorageCluster, FileSystemStorageNode, FileSystemVirtualDeviceMapper, FileSystemContentAddressIndex, FileSystemManifestRepository, FileSystemStoredObjectRepository, YAML policy/device/disk-set catalogs, compression/encryption/EC/replication adapters, direct SHA-256 calculation in filesystem/pipeline components, and FaultInjectingStorageCluster chaos decorator. MINIO_STANDARD YAML enables EC (4 data / 2 parity) and disables dedup; current C4 docs do not claim verified physical EC shard placement." "Java 21, Reactor, YAML"
        storageEngineRepositoryAdapter = component "object-store-reactive-repository-storage-engine-infrastructure" "Anti-Corruption Layer + adapter: implements Object Store repository interfaces using the Storage Engine backend. Contains ObjectStoreToStorageEngineTranslator, StorageEngineReactiveS3ObjectRepository, StorageEngineReactiveBucketRepository, StorageEngineReactiveMultipartUploadRepository." "Spring @Repository, Reactor"
      }

      clusterNode = container "Cluster Node Runtime" "Implemented and validated only for the bounded EP-10 first slice: one co-located voter/storage runtime, instantiated as fixed nodes A, B, and C. Each JVM owns one Ratis voter and one independent replica data server. Consensus orders bucket and immutable whole-object-reference generations; direct N=3/W=2 replication has no degraded writes. Internal only; not an object API and not a production-readiness claim." "Java 21, Apache Ratis 3.2.2, grpc-java 1.82.2, protobuf 3.25.8, mTLS" {
        tags "FirstSlice"
        storageEngineClusterApplication = component "storage-engine-cluster-application" "Implemented transport-neutral CreateBucket, whole-object write/read coordination, PA-6 placement, authoritative metadata ports, local-artifact ports, acknowledgement fencing, and replica-transfer ports. Reactor-facing where needed; contains no Ratis, protobuf, generated-stub, or gRPC types." "Java 21 application module"
        clusterProtocol = component "cluster-protocol" "Implemented versioned internal protobuf contracts and generated messages/stubs for health and immutable-artifact stage/read transfer. It contains no S3 facade, Spring application behavior, storage-policy decisions, or domain logic." "protobuf/protoc 3.25.8 module"
        clusterControlRatisInfrastructure = component "cluster-control-ratis-infrastructure" "Implemented embedded Ratis lifecycle, deterministic bucket/object-reference generation state machine, fixed A/B/C bootstrap manifest, stable UUID identity recovery, persisted log/snapshots, and identity-bound control mTLS. Dynamic membership is future scope." "Apache Ratis 3.2.2, ratis-grpc"
        clusterDataGrpcInfrastructure = component "cluster-data-grpc-infrastructure" "Implemented independent replica gRPC server/clients, peer identity checks, bounded 64 KiB framing and manual flow control, deadlines/cancellation, checksum validation, durable local artifacts, and replica acknowledgements." "grpc-java 1.82.2, grpc-netty-shaded"

        futureClusteredObjectSemantics = component "Future clustered object semantics" "PLANNED / NOT IMPLEMENTED: clustered multipart state and part transfer, conditional writes, S3 versioning, and chunked-object transfer. Existing single-node behavior is not cluster evidence." "Future cluster application capability" {
          tags "Planned"
        }
        futureErasureCodedTransfer = component "Future erasure-coded transfer" "PLANNED / NOT IMPLEMENTED: encode and transfer data/parity shards, apply policy-specific acknowledgement thresholds, and reconstruct reads. Whole-object N=3/W=2 replication is not erasure-coding evidence." "Future cluster application and data-plane capability" {
          tags "Planned"
        }
        futureMembershipLifecycle = component "Future dynamic membership lifecycle" "PLANNED / NOT IMPLEMENTED: consensus-controlled admission, catch-up, promotion, demotion, replacement, removal, fencing, certificate lifecycle, and rolling compatibility. Static A/B/C bootstrap is not dynamic membership." "Future control-plane capability" {
          tags "Planned"
        }
        futureRepairMovement = component "Future healing and rebalance execution" "PLANNED / NOT IMPLEMENTED: durable anti-entropy, healing, rebalance, and orphan-cleanup jobs. Existing planning models do not execute repair or movement." "Future cluster operations capability" {
          tags "Planned"
        }
        futurePartitionResilience = component "Future broader partition handling" "PLANNED / NOT VALIDATED: behavior for asymmetric partitions, delayed or reordered messages, split control/data paths, stale leaders, duplicate identities, and repeated coordinator changes. The validated baseline covers only one stopped coordinator while the surviving peers remain mutually reachable." "Future cluster resilience capability" {
          tags "Planned"
        }
      }
    }

    deploymentEnvironment "EP-10 Fixed Three-Node Runtime Baseline" {
      deploymentNode "Independent failure domains" "Bounded implemented topology: fixed nodes A, B, and C are placed in distinct available failure domains. This baseline is not a production topology recommendation. Production certificate enrollment, custody, rotation, revocation, expiry monitoring, recovery, and broader operational readiness remain outside the slice." "Deployment infrastructure" {
        tags "FirstSlice"
        deploymentNode "Node A JVM" "One Java process owns the S3 composition, one Ratis voter, and one independent replica data server. Its stable UUID is recovered from the identity root; consensus, object, temporary, and runtime roots are independent. Internal peers authenticate the UUID through configured mutual TLS trust." "Java 21 JVM" {
          tags "FirstSlice"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Node B JVM" "One Java process owns the S3 composition, one Ratis voter, and one independent replica data server. Its stable UUID and persisted roots survive complete restart; B serves failover reads with C retaining quorum after A stops." "Java 21 JVM" {
          tags "FirstSlice"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Node C JVM" "One Java process owns the S3 composition, one Ratis voter, and one independent replica data server. Its stable UUID and persisted roots survive complete restart; C retains control quorum with B after A stops." "Java 21 JVM" {
          tags "FirstSlice"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
      }
    }

    deploymentEnvironment "EP-10 Real-Process Acceptance Baseline" {
      deploymentNode "Isolated acceptance environment" "Validated test topology starts fixed A/B/C as real child JVM processes with separate roots, ephemeral test-local CA material, per-node certificates, and test-only trust stores. The fixtures exercise mTLS locally; they are not production PKI or production-readiness evidence." "Isolated real-process test environment" {
        tags "FirstSlice"
        deploymentNode "Acceptance node A" "Initial S3 coordinator and co-located voter/storage node; stopped after successful CreateBucket and whole-object PUT." "Java 21 child JVM" {
          tags "FirstSlice"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Acceptance node B" "Surviving S3 endpoint and co-located voter/storage node used for exact-byte GET and post-restart recovery." "Java 21 child JVM" {
          tags "FirstSlice"
          containerInstance magrathea.bootstrapApplication
          containerInstance magrathea.s3ReactiveApiAdapter
          containerInstance magrathea.storageEngine
          containerInstance magrathea.clusterNode
        }
        deploymentNode "Acceptance node C" "Surviving co-located voter/storage node that retains control quorum with B and recovers persisted state after complete restart." "Java 21 child JVM" {
          tags "FirstSlice"
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
    magrathea.browserFrontend.adminApiClient -> magrathea.adminApiAdapter.adminRouter "Calls implemented Admin Control Plane routes" "HTTP/JSON"
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
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.storageEngine.storageEngineDomain "Invokes pure PA-6 placement for fixed N=3/W=2 without adding transport or lifecycle types to storage-engine-domain" "In-process Java calls"
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "Resolves and commits bucket/object-reference generations through the fixed three-voter control group" "Transport-neutral control ports"
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "Stages, sends, or reads immutable whole-object artifacts and requires checksum-valid durable acknowledgements" "Transport-neutral replica ports"
    magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterProtocol "Uses versioned replica transfer contracts with bounded manual flow control" "Generated protobuf/gRPC contracts"
    magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "A/B/C voters commit bucket and object-reference generations; peers require stable-UUID-bound mTLS" "Ratis gRPC/HTTP2 with mTLS"
    magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "Selected A/B/C storage roles transfer whole-object replicas directly and return durable checksum-valid acknowledgements; peers require stable-UUID-bound mTLS" "Application gRPC/HTTP2 streaming with mTLS"

    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.futureClusteredObjectSemantics "PLANNED: extend the bounded object coordinator beyond unconditional whole-object PUT/GET" "Future in-process capability" {
      tags "Planned"
    }
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.futureErasureCodedTransfer "PLANNED: coordinate policy-specific shard placement, transfer, acknowledgement, and reconstruction" "Future in-process capability" {
      tags "Planned"
    }
    magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.futureMembershipLifecycle "PLANNED: commit safe membership and lifecycle transitions instead of editing fixed bootstrap" "Future control capability" {
      tags "Planned"
    }
    magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.futureRepairMovement "PLANNED: execute durable fenced healing, rebalance, and cleanup work" "Future operations capability" {
      tags "Planned"
    }
    magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.futurePartitionResilience "PLANNED: extend and validate control behavior beyond one stopped coordinator" "Future resilience capability" {
      tags "Planned"
    }
    magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.futurePartitionResilience "PLANNED: extend and validate data-path behavior under broader partitions and message faults" "Future resilience capability" {
      tags "Planned"
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
      description "Implemented boundaries remain unchanged: bootstrap-application assembles the S3 and Admin runtimes, the browser uses only those established interfaces, and storage-engine remains behind the Object Store ACL. ADR 0028 records the bounded implementation-informed EP-10 baseline: fixed co-located voter/storage nodes A/B/C for unconditional CreateBucket and whole-object PUT/GET. Each JVM owns one Ratis voter and one replica data server. Consensus orders bucket/object-reference generations; direct N=3/W=2 replica transfer has no degraded writes. Stable UUID identities and roots survive complete restart. Internal links use identity-bound mTLS. Fixed bootstrap is not dynamic membership, and this slice is not distributed production readiness."
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
      description "Executable application assembly and runtime composition boundary. It starts the S3 WebFlux application, starts the admin/static Netty server, selects the object-store backend, and serves static UI/docs/report assets copied into bootstrap-application during generate-resources. Static assets are no longer owned by admin-api-adapter."
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
      description "Implemented EP-7 frontend boundaries. The deployable application composes the product-neutral Product Shell and independently maintained Object Storage Product Extension, owns browser/platform and documentation integration, and injects separate typed Admin API and optional S3 HeadObject clients. Operational report screens preserve provider-not-configured responses; no absent report, audit, metrics, trace, recovery, garbage-collection, or scrub provider is modeled."
      include administrator
      include magrathea.bootstrapApplication
      include *
      include magrathea.adminApiAdapter
      include magrathea.s3ReactiveApiAdapter
      autolayout lr
    }

    component magrathea.adminApiAdapter "AdminApiAdapterComponents" {
      title "C3 Component: admin-api-adapter"
      description "Implemented JSON Admin Control Plane routes consumed by the browser application and mounted by the bootstrap admin server. Catalog and capacity behavior is backed by configured ports; operational reports explicitly remain unavailable when no matching real provider is configured. Frontend and generated documentation assets are handed off to bootstrap-application static resources."
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
      description "Reactive object and multipart upload application services, domain aggregates with state transitions and domain events, repository ports, and content boundary. Supports Phase F advanced operations (legal hold, retention, torrent, restore, select, Object Lambda, encryption update)."
      include magrathea.s3ReactiveApiAdapter
      include *
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include magrathea.reactiveInfrastructure
      autolayout lr
    }

    component magrathea.reactiveBucketManagement "ReactiveBucketManagementComponents" {
      title "C3 Component: reactive-bucket-management"
      description "Reactive bucket management service, bucket aggregate root with dedicated with* methods per config type and specific ObjectStoreEvent subtypes, and repository port. Supports Phase F advanced bucket config (ABAC, object lock, metadata/table config)."
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
      description "Reactive repository adapters used by the application capabilities. Extended to support session, ABAC, object lock, metadata/table configuration, legal hold, retention, restore, and select data. Internal ConcurrentHashMap structures are intentionally not shown as C4 components."
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include *
      autolayout lr
    }

    component magrathea.storageEngine "StorageEngineComponents" {
      title "C3 Component: storage-engine"
      description "Storage Engine bounded context: domain model, reactive repository/application ports, reactive orchestration layer, filesystem/YAML adapters, and Anti-Corruption Layer adapter that implements Object Store repository interfaces using the Storage Engine backend. The existing adapter remains the only S3 integration boundary. PA-6 storage-engine-domain remains pure; the bounded EP-10 cluster modules stay outside this container and call its transport-free placement model."
      include *
      include magrathea.clusterNode
      autolayout lr
    }

    component magrathea.clusterNode "ClusterNodeComponents" {
      title "C3 Component: Bounded First-Slice Cluster Node Runtime"
      description "IMPLEMENTED AND VALIDATED FOR THE BOUNDED FIRST SLICE ONLY. The node runtime contains transport-neutral cluster application code, versioned replica protocol, one embedded Ratis voter, and one independent grpc-java data server per JVM. Fixed A/B/C bootstrap, stable UUID-backed roots, identity-bound mTLS, N=3/W=2 whole-object replication, consensus publication, one-coordinator failover, and complete restart are in scope. This diagram excludes later capabilities and does not claim production readiness."
      include magrathea.storageEngine.storageEngineRepositoryAdapter
      include magrathea.storageEngine.storageEngineDomain
      include magrathea.clusterNode.storageEngineClusterApplication
      include magrathea.clusterNode.clusterProtocol
      include magrathea.clusterNode.clusterControlRatisInfrastructure
      include magrathea.clusterNode.clusterDataGrpcInfrastructure
      autolayout lr
    }

    component magrathea.clusterNode "FutureClusterCapabilities" {
      title "C3 Component: Future Cluster Capabilities — Planned / Not Implemented"
      description "DISTINCT FUTURE SCOPE, NOT PART OF THE VALIDATED FIRST SLICE. Clustered multipart and conditional/versioned writes, erasure-coded transfer/reconstruction, dynamic membership and certificate lifecycle, durable healing/rebalance/orphan cleanup, and broader partition behavior remain planned or unvalidated. Single-node features and PA-6 planning models do not upgrade these cluster capabilities."
      include magrathea.clusterNode.storageEngineClusterApplication
      include magrathea.clusterNode.clusterControlRatisInfrastructure
      include magrathea.clusterNode.clusterDataGrpcInfrastructure
      include magrathea.clusterNode.futureClusteredObjectSemantics
      include magrathea.clusterNode.futureErasureCodedTransfer
      include magrathea.clusterNode.futureMembershipLifecycle
      include magrathea.clusterNode.futureRepairMovement
      include magrathea.clusterNode.futurePartitionResilience
      autolayout lr
    }

    dynamic magrathea.browserFrontend "AdminStaticAssetRuntime" {
      title "Runtime: Object Storage Administration"
      description "The admin server delivers the independently built browser distribution and bundled documentation. The running browser application composes Product Shell with the Object Storage Product Extension and calls implemented Admin Control Plane routes. Operational report routes return provider-not-configured when no real provider exists; S3 HeadObject is attempted only when its separate endpoint and signer are configured."
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
      description "IMPLEMENTED AND VALIDATED FOR THE FIRST SLICE. The existing S3 and Object Store boundaries submit an unconditional CreateBucket through the Storage Engine ACL. The fixed A/B/C voter group commits the new bucket generation before S3 success. Dynamic membership and broader bucket configuration are outside this slice."
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
      description "IMPLEMENTED AND VALIDATED FOR THE FIRST SLICE. Node A coordinates one unconditional, single-part whole-object PUT. PA-6 selects fixed storage nodes A/B/C for N=3; direct replica transfer must obtain W=2 checksum-valid durable acknowledgements, then the A/B/C control quorum commits the object-reference generation before S3 success. There is no degraded write: fewer than two data acknowledgements or loss of control quorum fails publication."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. Submit unconditional whole-object S3 PutObject through node A" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "2. Invoke existing object write use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "3. Persist through the existing repository boundary" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository "4. Select the cluster-profile repository" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.storageEngine.storageEngineRepositoryAdapter "5. Enter the existing Storage Engine ACL" "Implements"
      magrathea.storageEngine.storageEngineRepositoryAdapter -> magrathea.clusterNode.storageEngineClusterApplication "6. Coordinate the transport-neutral whole-object write" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.storageEngine.storageEngineDomain "7. Use pure PA-6 placement to select A/B/C at N=3/W=2" "In-process Java calls"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterDataGrpcInfrastructure "8. Stage and directly transfer the immutable whole-object artifact" "Transport-neutral replica ports"
      magrathea.clusterNode.clusterDataGrpcInfrastructure -> magrathea.clusterNode.clusterDataGrpcInfrastructure "9. Return identity-bound checksum-valid durable acknowledgements from selected nodes" "Application gRPC/HTTP2 streaming with mTLS"
      magrathea.clusterNode.storageEngineClusterApplication -> magrathea.clusterNode.clusterControlRatisInfrastructure "10. Only after W=2, submit the verified object-reference generation" "Transport-neutral control ports"
      magrathea.clusterNode.clusterControlRatisInfrastructure -> magrathea.clusterNode.clusterControlRatisInfrastructure "11. Commit the reference through A/B/C quorum before S3 success" "Ratis gRPC/HTTP2 with mTLS"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "ClusterFailoverReadRuntime" {
      title "Runtime: Exact-Byte GET After Coordinator A Stops"
      description "IMPLEMENTED AND VALIDATED FOR THE FIRST-SLICE FAILOVER PROOF. After a successful whole-object PUT, coordinator A is stopped. B and C retain the two-voter control quorum. A client uses node B's existing S3 endpoint; B resolves the committed object-reference generation and returns bytes from a referenced checksum-valid replica. This proves only the narrow one-node-stop slice, not dynamic membership, healing, rebalance, broader partitions, upgrades, or production readiness."
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
      description "IMPLEMENTED AND VALIDATED FOR THE FIRST-SLICE RESTART PROOF. All three JVMs stop and discard process memory, then restart from their original non-empty roots. Each node recovers its stable UUID, Ratis state, immutable replicas, and committed bucket/object-reference generations. Reordered seeds do not rewrite persisted membership. An exact-byte GET through B verifies recovery; rolling upgrade and membership change are outside this proof."
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
      title "Runtime: Phase F Advanced Operations"
      description "Phase F advanced operations (legal hold, retention, torrent, restore, select, Object Lambda, rename, encryption update, ABAC, object lock, session, metadata/table config) follow the established RouterFunction dispatch and service delegation pattern. Two persistence implementations exist: in-memory (reactive-infrastructure) and storage-engine profile (storage-engine)."
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

    deployment magrathea "EP-10 Fixed Three-Node Runtime Baseline" "FixedThreeNodeRuntimeDeployment" {
      title "Deployment: Bounded Fixed A/B/C Runtime Baseline"
      description "IMPLEMENTED FIRST-SLICE TOPOLOGY; NOT A PRODUCTION-READINESS CLAIM. Three co-located voter/storage JVMs use an identical static manifest, stable UUID identities, and independent identity, Ratis, object, temporary, and runtime roots. Each JVM owns one Ratis voter and one replica data server. Control and direct replica links require identity-bound mutual TLS. Fixed bootstrap supports no add, promote, demote, replace, or remove operation; production PKI and operational readiness remain outside this slice."
      include *
      autolayout tb
    }

    deployment magrathea "EP-10 Real-Process Acceptance Baseline" "FixedThreeNodeAcceptanceDeployment" {
      title "Deployment: Validated Real-Process A/B/C Acceptance Baseline"
      description "IMPLEMENTED AND VALIDATED FOR THE BOUNDED TEST TOPOLOGY. Real child JVMs run isolated A/B/C roots with ephemeral test-local CA/certificate/trust fixtures. After CreateBucket and N=3/W=2 whole-object PUT through A, A stops and exact bytes are read through B while B+C retain quorum. A later complete stop/restart recovers stable identities, committed control state, and object bytes. Test certificates are not production identity, enrollment, rotation, revocation, recovery, or readiness evidence."
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
      element "FirstSlice" {
        background #2f855a
        color #ffffff
        stroke #22543d
      }
      element "Planned" {
        background #6b7280
        color #ffffff
        stroke #374151
      }
      relationship "Planned" {
        color #6b7280
        dashed true
      }
    }

    theme default
  }
}
