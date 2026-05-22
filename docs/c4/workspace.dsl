workspace "Magrathea ObjectStorage" "C4 model for the Magrathea S3-compatible object storage service" {

  !identifiers hierarchical

  model {
    user = person "User" "End user or automated client using AWS CLI, SDK, curl, or another S3-compatible HTTP client."

    magrathea = softwareSystem "Magrathea ObjectStorage" "AWS S3-compatible object storage built with Spring Boot 4 WebFlux and Java 21." {
      s3ApiAdapter = container "s3-api-adapter" "HTTP adapter that exposes the S3-compatible REST API and translates HTTP/XML/binary requests into application use cases." "Spring Boot 4 WebFlux, RouterFunction, Java 21, Jackson XML" {
        bucketOperationsHandler = component "S3BucketOperationsHandler" "Handles bucket lifecycle endpoints: create, delete, head, list, location and versioning." "Java WebFlux handler"
        bucketMetadataHandler = component "S3BucketMetadataHandler" "Handles bucket metadata endpoints such as ACL and tagging." "Java WebFlux handler"
        bucketConfigHandler = component "S3BucketConfigHandler" "Handles bucket configuration endpoints such as CORS." "Java WebFlux handler"
        objectOperationsHandler = component "S3ObjectOperationsHandler" "Handles object operations: put, get, head, delete, copy and multi-delete." "Java WebFlux handler"
        objectMetadataHandler = component "S3ObjectMetadataHandler" "Handles object metadata endpoints such as ACL, tagging and attributes." "Java WebFlux handler"
        multipartHandler = component "S3MultipartHandler" "Handles multipart upload lifecycle: initiate, upload part, list parts, complete, abort and list uploads." "Java WebFlux handler"
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
        inMemoryBucketRepository = component "InMemoryBucketRepository" "In-memory implementation of BucketRepository. Internally stores bucket aggregates and bucket configuration in ConcurrentHashMap structures." "Spring @Repository"
        inMemoryObjectRepository = component "InMemoryObjectRepository" "In-memory implementation of S3ObjectRepository. Internally stores object metadata and raw object bytes in ConcurrentHashMap structures." "Spring @Repository"
        inMemoryMultipartUploadRepository = component "InMemoryMultipartUploadRepository" "In-memory implementation of MultipartUploadRepository. Internally stores multipart upload sessions in a ConcurrentHashMap structure." "Spring @Repository"
      }
    }

    user -> magrathea.s3ApiAdapter.bucketOperationsHandler "Sends S3 bucket operations requests" "HTTP"
    user -> magrathea.s3ApiAdapter.bucketMetadataHandler "Sends S3 bucket metadata requests" "HTTP"
    user -> magrathea.s3ApiAdapter.bucketConfigHandler "Sends S3 bucket configuration requests" "HTTP"
    user -> magrathea.s3ApiAdapter.objectOperationsHandler "Sends S3 object operations requests" "HTTP"
    user -> magrathea.s3ApiAdapter.objectMetadataHandler "Sends S3 object metadata requests" "HTTP"
    user -> magrathea.s3ApiAdapter.multipartHandler "Sends S3 multipart upload requests" "HTTP"

    magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.bucketManagment.bucketService "Calls bucket lifecycle and listing use cases" "Java service calls"
    magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.objectStore.objectService "Calls object listing use cases" "Java service calls"
    magrathea.s3ApiAdapter.bucketMetadataHandler -> magrathea.bucketManagment.bucketService "Calls bucket metadata use cases" "Java service calls"
    magrathea.s3ApiAdapter.bucketConfigHandler -> magrathea.bucketManagment.bucketService "Calls bucket CORS/configuration use cases" "Java service calls"
    magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.bucketManagment.bucketService "Checks bucket existence" "Java service calls"
    magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.objectStore.objectService "Calls object CRUD/content use cases" "Java service calls"
    magrathea.s3ApiAdapter.objectMetadataHandler -> magrathea.bucketManagment.bucketService "Checks bucket existence" "Java service calls"
    magrathea.s3ApiAdapter.objectMetadataHandler -> magrathea.objectStore.objectService "Calls object metadata use cases" "Java service calls"
    magrathea.s3ApiAdapter.multipartHandler -> magrathea.objectStore.multipartUploadService "Calls multipart upload use cases" "Java service calls"

    magrathea.objectStore -> magrathea.bucketManagment "Validates bucket existence" "Bucket lookup/read collaboration"
    magrathea.objectStore -> magrathea.inMemoryRepository "Reads/writes object metadata, bytes and multipart upload state" "Repository interfaces"
    magrathea.bucketManagment -> magrathea.inMemoryRepository "Reads/writes bucket metadata and configuration" "Repository interfaces"

    magrathea.objectStore.objectService -> magrathea.objectStore.bucketRepositoryReadPort "Validates bucket existence" "Java service calls"
    magrathea.objectStore.objectService -> magrathea.objectStore.s3ObjectRepositoryPort "Persists and loads objects/content" "Repository interfaces"
    magrathea.objectStore.objectService -> magrathea.objectStore.s3Object "Creates/restores object metadata" "Java calls"
    magrathea.objectStore.objectService -> magrathea.objectStore.contentBoundary "Wraps upload/download content" "Java calls"
    magrathea.objectStore.objectService -> magrathea.inMemoryRepository.inMemoryObjectRepository "Persists object metadata and bytes" "Repository interfaces"
    magrathea.objectStore.multipartUploadService -> magrathea.objectStore.bucketRepositoryReadPort "Resolves bucket by name" "Java service calls"
    magrathea.objectStore.multipartUploadService -> magrathea.objectStore.multipartUploadRepositoryPort "Persists and loads multipart uploads" "Repository interfaces"
    magrathea.objectStore.multipartUploadService -> magrathea.objectStore.multipartUpload "Creates/updates multipart upload state" "Java calls"
    magrathea.objectStore.multipartUploadService -> magrathea.inMemoryRepository.inMemoryMultipartUploadRepository "Persists multipart upload state" "Repository interfaces"

    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.bucketRepositoryPort "Persists and loads buckets/configuration" "Repository interfaces"
    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.bucket "Creates/updates bucket aggregate" "Java calls"
    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.bucketConfiguration "Creates/updates bucket configuration" "Java calls"
    magrathea.bucketManagment.bucketService -> magrathea.bucketManagment.corsConfiguration "Maps CORS commands to domain rules" "Java calls"
    magrathea.bucketManagment.bucketService -> magrathea.inMemoryRepository.inMemoryBucketRepository "Persists and reads bucket aggregate" "Repository interfaces"

    magrathea.bucketManagment.bucketRepositoryPort -> magrathea.inMemoryRepository.inMemoryBucketRepository "Implemented by" "Implements"
    magrathea.objectStore.bucketRepositoryReadPort -> magrathea.inMemoryRepository.inMemoryBucketRepository "Implemented by" "Implements"
    magrathea.objectStore.s3ObjectRepositoryPort -> magrathea.inMemoryRepository.inMemoryObjectRepository "Implemented by" "Implements"
    magrathea.objectStore.multipartUploadRepositoryPort -> magrathea.inMemoryRepository.inMemoryMultipartUploadRepository "Implemented by" "Implements"
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

    dynamic magrathea.s3ApiAdapter "CreateBucketRuntime" {
      title "Runtime: CreateBucket"
      description "PUT /{bucket} creates a Bucket aggregate and persists it in in-memory-repository."
      user -> magrathea.s3ApiAdapter.bucketOperationsHandler "1. PUT /{bucket}" "HTTP"
      magrathea.s3ApiAdapter.bucketOperationsHandler -> magrathea.bucketManagment.bucketService "2. CreateBucket use case" "Java service calls"
      magrathea.bucketManagment.bucketService -> magrathea.inMemoryRepository.inMemoryBucketRepository "3. Save Bucket aggregate" "Repository interfaces"
      autolayout lr
    }

    dynamic magrathea.s3ApiAdapter "PutObjectRuntime" {
      title "Runtime: PutObject"
      description "PutObject verifies the bucket, then persists object metadata and content."
      user -> magrathea.s3ApiAdapter.objectOperationsHandler "1. PUT /{bucket}/{key}" "HTTP"
      magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.bucketManagment.bucketService "2. Verify bucket exists" "Java service calls"
      magrathea.bucketManagment.bucketService -> magrathea.inMemoryRepository.inMemoryBucketRepository "3. Read bucket metadata" "Repository interfaces"
      magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.objectStore.objectService "4. PutObject use case" "Java service calls"
      magrathea.objectStore.objectService -> magrathea.inMemoryRepository.inMemoryObjectRepository "5. Save object metadata and bytes" "Repository interfaces"
      autolayout lr
    }

    dynamic magrathea.s3ApiAdapter "GetObjectRuntime" {
      title "Runtime: GetObject"
      description "GetObject verifies the bucket, then loads object metadata and binary content."
      user -> magrathea.s3ApiAdapter.objectOperationsHandler "1. GET /{bucket}/{key}" "HTTP"
      magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.bucketManagment.bucketService "2. Verify bucket exists" "Java service calls"
      magrathea.bucketManagment.bucketService -> magrathea.inMemoryRepository.inMemoryBucketRepository "3. Read bucket metadata" "Repository interfaces"
      magrathea.s3ApiAdapter.objectOperationsHandler -> magrathea.objectStore.objectService "4. GetObject use case" "Java service calls"
      magrathea.objectStore.objectService -> magrathea.inMemoryRepository.inMemoryObjectRepository "5. Read object metadata and bytes" "Repository interfaces"
      autolayout lr
    }

    dynamic magrathea.s3ApiAdapter "MultipartUploadRuntime" {
      title "Runtime: Multipart upload lifecycle"
      description "Multipart endpoints manage upload sessions and uploaded part state."
      user -> magrathea.s3ApiAdapter.multipartHandler "1. POST/PUT/GET/DELETE multipart endpoints" "HTTP"
      magrathea.s3ApiAdapter.multipartHandler -> magrathea.objectStore.multipartUploadService "2. Multipart upload lifecycle use cases" "Java service calls"
      magrathea.objectStore.multipartUploadService -> magrathea.inMemoryRepository.inMemoryMultipartUploadRepository "3. Read/write multipart upload state" "Repository interfaces"
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
