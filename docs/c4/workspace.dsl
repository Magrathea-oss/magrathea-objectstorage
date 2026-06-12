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

      adminApiAdapter = container "admin-api-adapter" "Management HTTP adapter that exposes JSON admin endpoints such as health and storage policy operations. Frontend/static documentation assets are handed off to bootstrap-application and served from its classpath static resources." "Spring Boot 4 WebFlux, RouterFunction" {
        adminRouter = component "AdminRouter" "Defines /admin/health and /admin/storage-policies routes, maps storage policy requests to storage-engine domain value objects, and returns JSON/HATEOAS-style responses." "Spring @Configuration, RouterFunction"
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
        storageEngineReactiveRepositoryApplication = component "storage-engine-reactive-repository-application" "Reactive ports, catalogs, and repository interfaces used by the Storage Engine application: StoragePolicyCatalog, StorageDeviceCatalog, DiskSetCatalog, StoredObjectRepository, ObjectManifestRepository, ContentAddressIndex, ChecksumPort, DataTransformPort, AlterationPort, ChunkStorePort, and ECOutcome." "Java 21 CQS/port interfaces"
        storageEngineReactiveApplication = component "storage-engine-reactive-application" "Reactive orchestration and use cases: ReactiveStorageOrchestrator, Chunker, and ApplicationChunkPayload coordinate domain planning with repository/catalog/adapter ports." "Spring @Service, Reactor"
        storageEngineReactiveInfrastructure = component "storage-engine-reactive-infrastructure" "Filesystem and YAML adapters/configuration: FileSystemStorageCluster, FileSystemStorageNode, FileSystemVirtualDeviceMapper, FileSystemContentAddressIndex, FileSystemManifestRepository, FileSystemStoredObjectRepository, YAML policy/device/disk-set catalogs, checksum/compression/encryption/EC/replication adapters, and FaultInjectingStorageCluster chaos decorator. MINIO_STANDARD YAML enables EC (4 data / 2 parity) and disables dedup; current C4 docs do not claim verified physical EC shard placement." "Java 21, Reactor, YAML"
        storageEngineRepositoryAdapter = component "object-store-reactive-repository-storage-engine-infrastructure" "Anti-Corruption Layer + adapter: implements Object Store repository interfaces using the Storage Engine backend. Contains ObjectStoreToStorageEngineTranslator, StorageEngineReactiveS3ObjectRepository, StorageEngineReactiveBucketRepository, StorageEngineReactiveMultipartUploadRepository." "Spring @Repository, Reactor"
      }
    }

    administrator -> magrathea "Uses admin UI, generated documentation, and management API" "HTTP/JSON"
    administrator -> magrathea.bootstrapApplication.adminServerConfig "Uses bundled admin UI and generated documentation on the admin port" "HTTP"
    administrator -> magrathea.adminApiAdapter.adminRouter "Calls admin management endpoints" "HTTP/JSON"

    magrathea.bootstrapApplication.magratheaApplication -> magrathea.s3ReactiveApiAdapter.s3ProxyRouter "Bootstraps S3 API routes on the main WebFlux server" "Spring Boot auto-configuration"
    magrathea.bootstrapApplication.magratheaApplication -> magrathea.reactiveObjectStore.reactiveObjectService "Component-scans object-store services" "Spring component scan"
    magrathea.bootstrapApplication.magratheaApplication -> magrathea.reactiveBucketManagement.reactiveBucketService "Component-scans bucket-management services" "Spring component scan"
    magrathea.bootstrapApplication.adminServerConfig -> magrathea.adminApiAdapter.adminRouter "Mounts admin API routes on the admin server" "RouterFunction"
    magrathea.bootstrapApplication.adminServerConfig -> magrathea.bootstrapApplication.staticResources "Serves copied UI, documentation, and report assets from the bootstrap classpath" "ClassPathResource"
    magrathea.bootstrapApplication.backendSelection -> magrathea.reactiveInfrastructure "Selects in-memory repositories for the single-node profile" "Spring profiles/properties"
    magrathea.bootstrapApplication.backendSelection -> magrathea.storageEngine.storageEngineRepositoryAdapter "Selects Storage Engine-backed repositories for the storage-engine profile" "Spring profiles/properties"
    magrathea.adminApiAdapter.adminRouter -> magrathea.storageEngine.storageEngineDomain "Uses storage policy value objects for admin request mapping" "Java calls"

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
      description "bootstrap-application is the executable Spring Boot assembly and owns the static asset handoff for magrathea-ui dist assets, generated docs/reports, C4 images, and arc42 images; admin-api-adapter contributes only JSON admin routes. s3-reactive-api-adapter exposes the S3 API with RouterFunction dispatch; reactive-object-store and reactive-bucket-management implement core reactive capabilities with Phase F advanced operations; reactive-repository-application provides CQS interfaces; reactive-infrastructure persists state reactively in-process; and storage-engine provides the Storage Engine bounded context for persistence pipeline, virtual devices, storage policies, optional deduplication, erasure coding, content-addressed storage, and manifest management. MINIO_STANDARD is STANDARD with dedup disabled, EC enabled (4 data / 2 parity), replication factor 1, compression disabled, and encryption disabled by default."
      include user
      include administrator
      include magrathea.bootstrapApplication
      include magrathea.adminApiAdapter
      include magrathea.s3ReactiveApiAdapter
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include magrathea.reactiveInfrastructure
      include magrathea.storageEngine
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

    component magrathea.adminApiAdapter "AdminApiAdapterComponents" {
      title "C3 Component: admin-api-adapter"
      description "JSON management API routes consumed by the bootstrap admin server and the bundled UI. Frontend and generated documentation assets are handed off to bootstrap-application static resources."
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
      description "Storage Engine bounded context: domain model, reactive repository/application ports, reactive orchestration layer, filesystem/YAML adapters, and Anti-Corruption Layer adapter that implements Object Store repository interfaces using the Storage Engine backend. MINIO_STANDARD semantics are STANDARD with dedup disabled and EC enabled (4 data / 2 parity); this view does not claim verified physical EC shard placement."
      include *
      autolayout lr
    }

    dynamic magrathea.bootstrapApplication "AdminStaticAssetRuntime" {
      title "Runtime: Admin UI and Documentation Static Assets"
      description "The admin server in bootstrap-application serves the bundled UI and generated docs from classpath static resources; admin-api-adapter supplies only /admin JSON API routes."
      administrator -> magrathea.bootstrapApplication.adminServerConfig "1. GET /, /assets/**, or /docs/**" "HTTP"
      magrathea.bootstrapApplication.adminServerConfig -> magrathea.bootstrapApplication.staticResources "2. Serve index.html, assets, docs, C4 images, and reports" "ClassPathResource"
      administrator -> magrathea.bootstrapApplication.adminServerConfig "3. GET/POST/PUT/DELETE /admin/**" "HTTP"
      magrathea.bootstrapApplication.adminServerConfig -> magrathea.adminApiAdapter.adminRouter "4. Dispatch admin API route" "RouterFunction"
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
    }

    theme default
  }
}
