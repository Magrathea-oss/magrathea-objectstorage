workspace "Magrathea ObjectStore" "C4 model for the Magrathea S3-compatible object storage service" {

  !identifiers hierarchical

  model {
    user = person "User" "End user or automated client using AWS CLI, SDK, curl, or another S3-compatible HTTP client."

    kafka = softwareSystem "Kafka" "Event broker for notification events and access log streaming." {
      tags "external-system"
    }
    awsSns = softwareSystem "AWS SNS" "AWS Simple Notification Service for event notification delivery." {
      tags "external-system"
    }
    awsSqs = softwareSystem "AWS SQS" "AWS Simple Queue Service for event notification delivery." {
      tags "external-system"
    }
    elasticSearch = softwareSystem "ElasticSearch" "Search and analytics engine for access log indexing." {
      tags "external-system"
    }
    fileSystem = softwareSystem "File System" "Local file system or CSV/ORC format output for analytics data export destination." {
      tags "external-system"
    }
    awsS3TargetBucket = softwareSystem "AWS S3 Target Bucket" "AWS S3 bucket for log output and analytics data export destination." {
      tags "external-system"
    }
    prometheus = softwareSystem "Prometheus" "Monitoring and alerting system for metrics collection." {
      tags "external-system"
    }
    awsCloudWatch = softwareSystem "AWS CloudWatch" "AWS monitoring and observability service for metrics and logs." {
      tags "external-system"
    }
    micrometer = softwareSystem "Micrometer" "Metrics instrumentation library facade for collecting measurements." {
      tags "external-system"
    }

    magrathea = softwareSystem "Magrathea ObjectStore" "AWS S3-compatible object storage built with Spring Boot 4 WebFlux and Java 21." {
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

      storageEngine = container "storage-engine" "Future implementation module for extensible interfaces: event notification publishing, access log writing, metrics collection, and analytics data export. These interfaces are defined in object-store-domain and will be implemented here." "Java 21, domain interfaces" {
      tags "planned"
        notificationEventPublisher = component "NotificationEventPublisher" "Extensible event publishing interface for S3 event notifications (s3:ObjectCreated, s3:ObjectRemoved). Publishes to TopicConfiguration, QueueConfiguration, CloudFunctionConfiguration destinations." "Domain interface"
        accessLogWriter = component "AccessLogWriter" "Extensible interface for writing S3 access logs to external destinations (Kafka, ElasticSearch, file system, S3 target bucket)." "Domain interface"
        metricsCollector = component "MetricsCollector" "Extensible interface for recording metrics to CloudWatch, Prometheus, or Micrometer backends." "Domain interface"
        analyticsDataExporter = component "AnalyticsDataExporter" "Extensible interface for exporting analytics data snapshots to destination buckets (S3 target, CSV/ORC output)." "Domain interface"
      }
    }

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

    magrathea.storageEngine.notificationEventPublisher -> kafka "Publishes S3 events (s3:ObjectCreated, s3:ObjectRemoved)" "Event topic"
    magrathea.storageEngine.notificationEventPublisher -> awsSns "Publishes S3 events to SNS topics" "TopicConfiguration"
    magrathea.storageEngine.notificationEventPublisher -> awsSqs "Publishes S3 events to SQS queues" "QueueConfiguration"

    magrathea.storageEngine.accessLogWriter -> kafka "Writes access logs" "Log topic"
    magrathea.storageEngine.accessLogWriter -> elasticSearch "Writes access logs" "Index"
    magrathea.storageEngine.accessLogWriter -> fileSystem "Writes access logs" "File output"
    magrathea.storageEngine.accessLogWriter -> awsS3TargetBucket "Writes access logs" "Target bucket"

    magrathea.storageEngine.metricsCollector -> prometheus "Records metrics" "Prometheus format"
    magrathea.storageEngine.metricsCollector -> awsCloudWatch "Records metrics" "CloudWatch API"
    magrathea.storageEngine.metricsCollector -> micrometer "Records metrics" "Micrometer facade"

    magrathea.storageEngine.analyticsDataExporter -> awsS3TargetBucket "Exports analytics data" "Target bucket"
    magrathea.storageEngine.analyticsDataExporter -> fileSystem "Exports analytics data" "CSV/ORC format output"
    magrathea.reactiveObjectStore -> magrathea.storageEngine.notificationEventPublisher "Emits domain events (s3:ObjectCreated, s3:ObjectRemoved)" "Domain events"
    magrathea.s3ReactiveApiAdapter -> magrathea.storageEngine.accessLogWriter "Sends HTTP request/response log entries" "Access log"
    magrathea.reactiveObjectStore -> magrathea.storageEngine.metricsCollector "Records operation metrics (count, size, latency)" "Metrics"
    magrathea.reactiveBucketManagement -> magrathea.storageEngine.analyticsDataExporter "Triggers analytics data export snapshots" "Export trigger"
  }

  views {
    systemContext magrathea "SystemContext" {
      title "C1 System Context: Magrathea ObjectStore"
      description "External S3-compatible clients interact with Magrathea ObjectStore as a single software system."
      include user
      include magrathea
      autolayout lr
    }

    container magrathea "Container" {
      title "C2 Container: Magrathea ObjectStore"
      description "s3-reactive-api-adapter exposes the S3 API with RouterFunction dispatch; reactive-object-store and reactive-bucket-management implement core reactive capabilities with Phase F advanced operations; reactive-repository-application provides CQS interfaces; reactive-infrastructure persists state reactively in-process; and storage-engine provides extensible interfaces for notification, logging, metrics, and analytics."
      include user
      include magrathea.s3ReactiveApiAdapter
      include magrathea.reactiveObjectStore
      include magrathea.reactiveBucketManagement
      include magrathea.reactiveRepositoryApplication
      include magrathea.reactiveInfrastructure
      include magrathea.storageEngine
      include kafka
      include awsSns
      include awsSqs
      include elasticSearch
      include fileSystem
      include awsS3TargetBucket
      include prometheus
      include awsCloudWatch
      include micrometer
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
      description "Extensible domain interfaces for event notification publishing, access log writing, metrics collection, and analytics data export. Implementation is POSTPONED — only interfaces are defined now in object-store-domain."
      include *
      include kafka
      include awsSns
      include awsSqs
      include elasticSearch
      include fileSystem
      include awsS3TargetBucket
      include prometheus
      include awsCloudWatch
      include micrometer
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
      description "PutObject verifies the bucket, then persists object metadata and content."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. PUT /{bucket}/{key}" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. Verify bucket exists" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "3. Read bucket aggregate via domain repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketQueryRepository "4. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "5. Read bucket aggregate" "Implements"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "6. PutObject use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "7. Persist object metadata and content via domain repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository "8. CQS command interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "9. Persist object metadata and bytes" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "GetObjectRuntime" {
      title "Runtime: GetObject"
      description "GetObject verifies the bucket, then loads object metadata and binary content."
      user -> magrathea.s3ReactiveApiAdapter.objectOperationsHandler "1. GET /{bucket}/{key}" "HTTP"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveBucketManagement.reactiveBucketService "2. Verify bucket exists" "Java service calls"
      magrathea.reactiveBucketManagement.reactiveBucketService -> magrathea.reactiveBucketManagement.bucketRepositoryPort "3. Read bucket aggregate via domain repository port" "Repository interfaces"
      magrathea.reactiveBucketManagement.bucketRepositoryPort -> magrathea.reactiveRepositoryApplication.bucketQueryRepository "4. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.bucketQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "5. Read bucket aggregate" "Implements"
      magrathea.s3ReactiveApiAdapter.objectOperationsHandler -> magrathea.reactiveObjectStore.reactiveObjectService "6. GetObject use case" "Java service calls"
      magrathea.reactiveObjectStore.reactiveObjectService -> magrathea.reactiveObjectStore.s3ObjectRepositoryPort "7. Read object metadata and content via domain repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.s3ObjectRepositoryPort -> magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository "8. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.s3ObjectQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "9. Read object metadata and bytes" "Implements"
      autolayout lr
    }

    dynamic magrathea.s3ReactiveApiAdapter "MultipartUploadRuntime" {
      title "Runtime: Multipart upload lifecycle"
      description "Multipart endpoints manage upload sessions and uploaded part state."
      user -> magrathea.s3ReactiveApiAdapter.multipartHandler "1. POST/PUT/GET/DELETE multipart endpoints" "HTTP"
      magrathea.s3ReactiveApiAdapter.multipartHandler -> magrathea.reactiveObjectStore.reactiveMultipartUploadService "2. Multipart upload lifecycle use cases" "Java service calls"
      magrathea.reactiveObjectStore.reactiveMultipartUploadService -> magrathea.reactiveObjectStore.multipartUploadRepositoryPort "3. Persist/load multipart upload state via domain repository port" "Repository interfaces"
      magrathea.reactiveObjectStore.multipartUploadRepositoryPort -> magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository "4. CQS command interface" "Implemented by"
      magrathea.reactiveObjectStore.multipartUploadRepositoryPort -> magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository "5. CQS query interface" "Implemented by"
      magrathea.reactiveRepositoryApplication.multipartUploadCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveMultipartUploadRepository "6. Persist multipart upload state" "Implements"
      magrathea.reactiveRepositoryApplication.multipartUploadQueryRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveMultipartUploadRepository "7. Read multipart upload state" "Implements"
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
      description "Phase F advanced operations (legal hold, retention, torrent, restore, select, Object Lambda, rename, encryption update, ABAC, object lock, session, metadata/table config) follow the established RouterFunction dispatch and service delegation pattern."
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
      magrathea.reactiveRepositoryApplication.s3ObjectCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveS3ObjectRepository "15. Persist/load S3Object data" "Implements"
      magrathea.reactiveRepositoryApplication.bucketCommandRepository -> magrathea.reactiveInfrastructure.inMemoryReactiveBucketRepository "16. Persist/load bucket data" "Implements"
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
      element "external-system" {
        background #6b7b8f
        color #ffffff
        shape roundedBox
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
      element "planned" {
        background #b8cce4
        color #000000
        border dashed
      }
    }

    theme default
  }
}
