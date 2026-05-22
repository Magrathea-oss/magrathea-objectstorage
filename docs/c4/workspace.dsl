workspace "Magrathea ObjectStorage" "C4 model for the Magrathea S3-compatible object storage service" {

  !identifiers hierarchical

  model {
    user = person "User" "End user or automated client using AWS CLI, SDK, curl, or another S3-compatible HTTP client."

    magrathea = softwareSystem "Magrathea ObjectStorage" "AWS S3-compatible object storage built with Spring Boot 4 WebFlux and Java 21." {
      s3ApiAdapter = container "s3-api-adapter" "HTTP adapter that exposes the S3-compatible REST API and translates HTTP/XML/binary requests into application use cases." "Spring Boot 4 WebFlux, RouterFunction, Java 21, Jackson XML" {
        s3ProxyRouter = component "S3ProxyRouter" "Entry point RouterFunction: defines S3-compatible routes and dispatches requests to specialised handlers." "Spring WebFlux RouterFunction"
        bucketOperationsHandler = component "S3BucketOperationsHandler" "Handles bucket lifecycle endpoints: create, delete, head, list, location and versioning." "Java WebFlux handler"
        bucketMetadataHandler = component "S3BucketMetadataHandler" "Handles bucket metadata endpoints such as ACL and tagging." "Java WebFlux handler"
        bucketConfigHandler = component "S3BucketConfigHandler" "Handles bucket configuration endpoints such as CORS." "Java WebFlux handler"
        objectOperationsHandler = component "S3ObjectOperationsHandler" "Handles object operations: put, get, head, delete, copy and multi-delete." "Java WebFlux handler"
        objectMetadataHandler = component "S3ObjectMetadataHandler" "Handles object metadata endpoints such as ACL, tagging and attributes." "Java WebFlux handler"
        multipartHandler = component "S3MultipartHandler" "Handles multipart upload lifecycle: initiate, upload part, list parts, complete, abort and list uploads." "Java WebFlux handler"
        webSupport = component "S3WebSupport" "Shared request predicates, request parsing, lookup helpers and S3-compatible error helpers." "Java utility"
        xmlResponses = component "S3XmlResponses" "S3-compatible XML response and error DTO factory." "Jackson XML records"
      }

      objectStore = container "object-store" "Object storage capability: object upload, download, metadata lookup, deletion and multipart object workflows." "Java 21 application/domain services" {
        objectService = component "ObjectService" "Application service for object CRUD, metadata lookup, object listing and binary content retrieval." "Spring @Service"
        multipartUploadService = component "MultipartUploadService" "Application service for multipart upload sessions and part lifecycle." "Spring @Service"
        s3Object = component "S3Object" "Domain aggregate for object identity, bucket id, key, ETag, storage class and content metadata." "Domain aggregate"
        multipartUpload = component "MultipartUpload" "Domain aggregate for multipart upload state, uploaded parts and completion/abort status." "Domain aggregate"
        s3ObjectRepositoryPort = component "S3ObjectRepository port" "Repository port for object metadata and binary content persistence." "Domain repository interface"
        multipartUploadRepositoryPort = component "MultipartUploadRepository port" "Repository port for multipart upload persistence." "Domain repository interface"
        bucketRepositoryReadPort = component "BucketRepository read port" "Read-side bucket repository port used by object-store to validate bucket existence." "Domain repository interface"
        contentBoundary = component "DefaultS3ObjectWrite / DefaultS3ObjectContent" "Application-layer content boundary for carrying Flux&lt;DataBuffer&gt; without leaking reactive types into the domain model." "Java adapter"
      }

      bucketManagment = container "bucket-managment" "Bucket management capability: bucket lifecycle, metadata, configuration, CORS/versioning and bucket-level operations." "Java 21 application/domain services" {
        bucketService = component "BucketService" "Application service for bucket lifecycle, versioning and CORS configuration." "Spring @Service"
        bucket = component "Bucket" "Domain aggregate for bucket identity, name, region, storage class and versioning/encryption state." "Domain aggregate"
        bucketConfiguration = component "BucketConfiguration" "Domain value object representing bucket-level configuration such as CORS rules." "Domain value object"
        corsConfiguration = component "CorsConfiguration" "Domain value object for CORS-related configuration." "Domain value object"
        bucketRepositoryPort = component "BucketRepository port" "Repository port for bucket metadata and bucket configuration persistence." "Domain repository interface"
      }

      inMemoryRepository = container "in-memory-repository" "In-process repository implementation for bucket metadata, object metadata/content and multipart upload state. It is not an external database and is reset when the process stops." "Java 21, ConcurrentHashMap" "Database" {
        bucketRepositoryImpl = component "BucketRepositoryImpl" "In-memory implementation of BucketRepository. Internally stores bucket aggregates and bucket configuration in ConcurrentHashMap structures." "Spring @Repository"
        inMemoryObjectRepository = component "InMemoryObjectRepository" "In-memory implementation of S3ObjectRepository. Internally stores object metadata and raw object bytes in ConcurrentHashMap structures." "Spring @Repository"
        inMemoryMultipartUploadRepository = component "InMemoryMultipartUploadRepository" "In-memory implementation of MultipartUploadRepository. Internally stores multipart upload sessions in a ConcurrentHashMap structure." "Spring @Repository"
      }
    }

    user -> magrathea "Uses" "S3-compatible REST API over HTTP"
    user -> magrathea.s3ApiAdapter "Sends S3 requests" "HTTP"

    magrathea.s3ApiAdapter -> magrathea.objectStore "Delegates object CRUD, content and multipart use cases" "Java service calls"
    magrathea.s3ApiAdapter -> magrathea.bucketManagment "Delegates bucket lifecycle, metadata and configuration use cases" "Java service calls"
    magrathea.objectStore -> magrathea.bucketManagment "Validates bucket existence" "Bucket lookup/read collaboration"
    magrathea.objectStore -> magrathea.inMemoryRepository "Reads/writes object metadata, bytes and multipart upload state" "Repository interfaces"
    magrathea.bucketManagment -> magrathea.inMemoryRepository "Reads/writes bucket metadata and configuration" "Repository interfaces"

    magrathea.s3ApiAdapter.s3ProxyRouter -> magrathea.s3ApiAdapter.bucketOperationsHandler "Routes bucket lifecycle/listing requests"
    magrathea.s3ApiAdapter.s3ProxyRouter -> magrathea.s3ApiAdapter.bucketMetadataHandler "Routes bucket metadata requests"
    magrathea.s3ApiAdapter.s3ProxyRouter -> magrathea.s3ApiAdapter.bucketConfigHandler "Routes bucket configuration requests"
    magrathea.s3ApiAdapter.s3ProxyRouter -> magrathea.s3ApiAdapter.objectOperationsHandler "Routes object CRUD requests"
    magrathea.s3ApiAdapter.s3ProxyRouter -> magrathea.s3ApiAdapter.objectMetadataHandler "Routes object metadata requests"
    magrathea.s3ApiAdapter.s3ProxyRouter -> magrathea.s3ApiAdapter.multipartHandler "Routes multipart upload requests"

    magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.bucketManagment.bucketService "Calls bucket lifecycle and listing use cases"
    magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.objectStore.objectService "Calls object listing use cases"
    magrathea.s3ApiAdapter.bucketMetadataHandler -> magrathea.bucketManagment.bucketService "Calls bucket metadata use cases"
    magrathea.s3ApiAdapter.bucketConfigHandler -> magrathea.bucketManagment.bucketService "Calls bucket CORS/configuration use cases"
    magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.bucketManagment.bucketService "Checks bucket existence"
    magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.objectStore.objectService "Calls object CRUD/content use cases"
    magrathea.s3ApiAdapter.objectMetadataHandler -> magrathea.bucketManagment.bucketService "Checks bucket existence"
    magrathea.s3ApiAdapter.objectMetadataHandler -> magrathea.objectStore.objectService "Calls object metadata use cases"
    magrathea.s3ApiAdapter.multipartHandler -> magrathea.objectStore.multipartUploadService "Calls multipart upload use cases"

    magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.s3ApiAdapter.webSupport "Uses request/error helpers"
    magrathea.s3ApiAdapter.bucketMetadataHandler -> magrathea.s3ApiAdapter.webSupport "Uses request/error helpers"
    magrathea.s3ApiAdapter.bucketConfigHandler -> magrathea.s3ApiAdapter.webSupport "Uses request/error helpers"
    magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.s3ApiAdapter.webSupport "Uses request/error helpers"
    magrathea.s3ApiAdapter.objectMetadataHandler -> magrathea.s3ApiAdapter.webSupport "Uses request/error helpers"
    magrathea.s3ApiAdapter.multipartHandler -> magrathea.s3ApiAdapter.webSupport "Uses request/error helpers"

    magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.s3ApiAdapter.xmlResponses "Builds XML responses"
    magrathea.s3ApiAdapter.bucketMetadataHandler -> magrathea.s3ApiAdapter.xmlResponses "Builds XML responses"
    magrathea.s3ApiAdapter.bucketConfigHandler -> magrathea.s3ApiAdapter.xmlResponses "Builds XML responses"
    magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.s3ApiAdapter.xmlResponses "Builds XML responses"
    magrathea.s3ApiAdapter.objectMetadataHandler -> magrathea.s3ApiAdapter.xmlResponses "Builds XML responses"
    magrathea.s3ApiAdapter.multipartHandler -> magrathea.s3ApiAdapter.xmlResponses "Builds XML responses"

    magrathea.objectStore.objectService -> magrathea.objectStore.bucketRepositoryReadPort "Validates bucket existence"
    magrathea.objectStore.objectService -> magrathea.objectStore.s3ObjectRepositoryPort "Persists and loads objects/content"
    magrathea.objectStore.objectService -> magrathea.objectStore.s3Object "Creates/restores object metadata"
    magrathea.objectStore.objectService -> magrathea.objectStore.contentBoundary "Wraps upload/download content"
    magrathea.objectStore.multipartUploadService -> magrathea.objectStore.bucketRepositoryReadPort "Resolves bucket by name"
    magrathea.objectStore.multipartUploadService -> magrathea.objectStore.multipartUploadRepositoryPort "Persists and loads multipart uploads"
    magrathea.objectStore.multipartUploadService -> magrathea.objectStore.multipartUpload "Creates/updates multipart upload state"

    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.bucketRepositoryPort "Persists and loads buckets/configuration"
    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.bucket "Creates/updates bucket aggregate"
    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.bucketConfiguration "Creates/updates bucket configuration"
    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.corsConfiguration "Maps CORS commands to domain rules"

    magrathea.bucketManagment.bucketRepositoryPort -> magrathea.inMemoryRepository.bucketRepositoryImpl "Implemented by"
    magrathea.objectStore.bucketRepositoryReadPort -> magrathea.inMemoryRepository.bucketRepositoryImpl "Implemented by"
    magrathea.objectStore.s3ObjectRepositoryPort -> magrathea.inMemoryRepository.inMemoryObjectRepository "Implemented by"
    magrathea.objectStore.multipartUploadRepositoryPort -> magrathea.inMemoryRepository.inMemoryMultipartUploadRepository "Implemented by"
  }

  views {
    systemContext magrathea "SystemContext" {
      title "C1 System Context: Magrathea ObjectStorage"
      description "External S3-compatible clients interact with Magrathea ObjectStorage as a single software system."
      include user
      include magrathea
      autolayout lr
    }

    container magrathea "Container" {
      title "C2 Container: Magrathea ObjectStorage"
      description "s3-api-adapter exposes the S3 API, object-store and bucket-managment implement core capabilities, and in-memory-repository persists state in-process."
      include user
      include magrathea.s3ApiAdapter
      include magrathea.objectStore
      include magrathea.bucketManagment
      include magrathea.inMemoryRepository
      autolayout lr
    }

    component magrathea.s3ApiAdapter "S3ApiAdapterComponents" {
      title "C3 Component: s3-api-adapter"
      description "RouterFunction and handler components that expose the S3-compatible HTTP protocol."
      include user
      include *
      include magrathea.objectStore
      include magrathea.bucketManagment
      autolayout lr
    }

    component magrathea.objectStore "ObjectStoreComponents" {
      title "C3 Component: object-store"
      description "Object and multipart upload application services, domain aggregates and repository ports."
      include magrathea.s3ApiAdapter
      include *
      include magrathea.bucketManagment
      include magrathea.inMemoryRepository
      autolayout lr
    }

    component magrathea.bucketManagment "BucketManagmentComponents" {
      title "C3 Component: bucket-managment"
      description "Bucket management service, bucket domain objects and repository port."
      include magrathea.s3ApiAdapter
      include magrathea.objectStore
      include *
      include magrathea.inMemoryRepository
      autolayout lr
    }

    component magrathea.inMemoryRepository "PersistenceComponents" {
      title "C3 Component: in-memory-repository"
      description "Repository adapters used by the application capabilities. Internal ConcurrentHashMap structures are intentionally not shown as C4 components."
      include magrathea.objectStore
      include magrathea.bucketManagment
      include *
      autolayout lr
    }

    dynamic magrathea "CreateBucketRuntime" {
      title "Runtime: CreateBucket"
      description "PUT /{bucket} creates a Bucket aggregate and persists it in in-memory-repository."
      user -> magrathea.s3ApiAdapter "1. PUT /{bucket}" "HTTP"
      magrathea.s3ApiAdapter -> magrathea.bucketManagment "2. CreateBucket use case" "Java service calls"
      magrathea.bucketManagment -> magrathea.inMemoryRepository "3. Save Bucket aggregate" "Repository interfaces"
      autolayout lr
    }

    dynamic magrathea "PutObjectRuntime" {
      title "Runtime: PutObject"
      description "PutObject verifies the bucket, then persists object metadata and content."
      user -> magrathea.s3ApiAdapter "1. PUT /{bucket}/{key}" "HTTP"
      magrathea.s3ApiAdapter -> magrathea.bucketManagment "2. Verify bucket exists" "Java service calls"
      magrathea.bucketManagment -> magrathea.inMemoryRepository "3. Read bucket metadata" "Repository interfaces"
      magrathea.s3ApiAdapter -> magrathea.objectStore "4. PutObject use case" "Java service calls"
      magrathea.objectStore -> magrathea.inMemoryRepository "5. Save object metadata and bytes" "Repository interfaces"
      autolayout lr
    }

    dynamic magrathea "GetObjectRuntime" {
      title "Runtime: GetObject"
      description "GetObject verifies the bucket, then loads object metadata and binary content."
      user -> magrathea.s3ApiAdapter "1. GET /{bucket}/{key}" "HTTP"
      magrathea.s3ApiAdapter -> magrathea.bucketManagment "2. Verify bucket exists" "Java service calls"
      magrathea.bucketManagment -> magrathea.inMemoryRepository "3. Read bucket metadata" "Repository interfaces"
      magrathea.s3ApiAdapter -> magrathea.objectStore "4. GetObject use case" "Java service calls"
      magrathea.objectStore -> magrathea.inMemoryRepository "5. Read object metadata and bytes" "Repository interfaces"
      autolayout lr
    }

    dynamic magrathea "MultipartUploadRuntime" {
      title "Runtime: Multipart upload lifecycle"
      description "Multipart endpoints manage upload sessions and uploaded part state."
      user -> magrathea.s3ApiAdapter "1. POST/PUT/GET/DELETE multipart endpoints" "HTTP"
      magrathea.s3ApiAdapter -> magrathea.objectStore "2. Multipart upload lifecycle use cases" "Java service calls"
      magrathea.objectStore -> magrathea.inMemoryRepository "3. Read/write multipart upload state" "Repository interfaces"
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
