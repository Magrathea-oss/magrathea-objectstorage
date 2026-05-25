# S3 Bucket Configuration Design — AWS API Study

> ADR 0011 prerequisite. Produced by java-planner.
> Based on official AWS S3 API documentation (fetched live from docs.aws.amazon.com).
>
> **ARC42 references:**
> - Section 4 (Solution Strategy) — ADR 0011 context and the redesign decision
> - Section 5 (Building Block View) — Bucket aggregate design with dedicated config methods
> - Section 8 (Cross-cutting Concepts) — Domain events design and handler integration patterns
>
> This document focuses on the AWS API reference material (XML structures, field names, constraints).
> Architecture-level decisions, building-block design, and cross-cutting concepts live in ARC42.

## Objective

Design proper domain models for each S3 bucket configuration feature, replacing the naive flat `Bucket.Configuration` approach. Each feature gets dedicated methods, specific domain events, and correct handler integration.

---

## 1. CORS Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?cors`
- **Response XML** (from official AWS docs):
```xml
<CORSConfiguration>
   <CORSRule>
      <AllowedHeader>string</AllowedHeader> ...
      <AllowedMethod>string</AllowedMethod> ...
      <AllowedOrigin>string</AllowedOrigin> ...
      <ExposeHeader>string</ExposeHeader> ...
      <ID>string</ID>
      <MaxAgeSeconds>integer</MaxAgeSeconds>
   </CORSRule> ...
</CORSConfiguration>
```
- **Purpose**: Cross-origin resource sharing rules for browser-based access

### Domain Model
- Already correct: `Bucket.Configuration.corsRules` with `CorsRule` nested record
- `hasCors()` / `getCors()` — already exist
- **Event**: `BucketCorsConfigurationChanged` (specific event)
- **No constraints** with other configs
- **Handler**: Already working — GET reads from config, PUT parses command, DELETE clears

---

## 2. Lifecycle Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?lifecycle`
- **Response XML** (from official AWS docs):
```xml
<LifecycleConfiguration>
   <Rule>
      <AbortIncompleteMultipartUpload>
         <DaysAfterInitiation>integer</DaysAfterInitiation>
      </AbortIncompleteMultipartUpload>
      <Expiration>
         <Date>timestamp</Date>
         <Days>integer</Days>
         <ExpiredObjectDeleteMarker>boolean</ExpiredObjectDeleteMarker>
      </Expiration>
      <Filter>
         <And>
            <ObjectSizeGreaterThan>long</ObjectSizeGreaterThan>
            <ObjectSizeLessThan>long</ObjectSizeLessThan>
            <Prefix>string</Prefix>
            <Tag><Key>string</Key><Value>string</Value></Tag> ...
         </And>
         <ObjectSizeGreaterThan>long</ObjectSizeGreaterThan>
         <ObjectSizeLessThan>long</ObjectSizeLessThan>
         <Prefix>string</Prefix>
         <Tag><Key>string</Key><Value>string</Value></Tag>
      </Filter>
      <ID>string</ID>
      <NoncurrentVersionExpiration>
         <NewerNoncurrentVersions>integer</NewerNoncurrentVersions>
         <NoncurrentDays>integer</NoncurrentDays>
      </NoncurrentVersionExpiration>
      <NoncurrentVersionTransition>
         <NewerNoncurrentVersions>integer</NewerNoncurrentVersions>
         <NoncurrentDays>integer</NoncurrentDays>
         <StorageClass>string</StorageClass>
      </NoncurrentVersionTransition> ...
      <Prefix>string</Prefix>
      <Status>string</Status>
      <Transition>
         <Date>timestamp</Date>
         <Days>integer</Days>
         <StorageClass>string</StorageClass>
      </Transition> ...
   </Rule> ...
</LifecycleConfiguration>
```
- **Purpose**: Define rules for transitioning objects between storage classes or expiring them
- **Rule fields**: `ID`, `Status` (Enabled/Disabled), `Filter` (prefix, tags, object size), and actions:
  - `Expiration` (days, date, expired object delete marker)
  - `NoncurrentVersionExpiration` (noncurrent days + newer noncurrent versions)
  - `AbortIncompleteMultipartUpload` (days after initiation)
  - `Transition` (days or date to storage class)
  - `NoncurrentVersionTransition` (noncurrent days + newer noncurrent versions to storage class)

### Domain Model
- **Current**: `BucketLifecycleConfiguration` value object — exists but needs richer structure
- **Integration**: `withLifecycleConfiguration(BucketLifecycleConfiguration)` method
- **Event**: `BucketLifecycleConfigurationChanged`
- **Constraints**: Multiple rules allowed; rules can overlap

---

## 3. Encryption Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?encryption`
- **Response XML** (from official AWS docs):
```xml
<ServerSideEncryptionConfiguration>
   <Rule>
      <ApplyServerSideEncryptionByDefault>
         <KMSMasterKeyID>string</KMSMasterKeyID>
         <SSEAlgorithm>string</SSEAlgorithm>
      </ApplyServerSideEncryptionByDefault>
      <BlockedEncryptionTypes>
         <EncryptionType>string</EncryptionType> ...
      </BlockedEncryptionTypes>
      <BucketKeyEnabled>boolean</BucketKeyEnabled>
   </Rule> ...
</ServerSideEncryptionConfiguration>
```
- **Purpose**: Default encryption behavior for objects (SSE-KMS, SSE-S3, SSE-C)
- **Note**: Root element is `ServerSideEncryptionConfiguration`, NOT `EncryptionConfiguration`
- **Separate** from the `encryptionEnabled` flag on Bucket

### Domain Model
- **Current**: `BucketEncryptionConfiguration` value object — exists
- **Integration**: `withEncryptionConfiguration(BucketEncryptionConfiguration)`
- **Event**: `BucketEncryptionConfigurationChanged`
- **Constraints**: At most one encryption configuration per bucket
- **Implementation**: belongs in the storage-engine module — postponed until storage-engine design phase

---

## 4. Logging Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?logging`
- **Response XML** (from official AWS docs):
```xml
<BucketLoggingStatus>
   <LoggingEnabled>
      <TargetBucket>string</TargetBucket>
      <TargetGrants>
         <Grant>
            <Grantee>
               <DisplayName>string</DisplayName>
               <EmailAddress>string</EmailAddress>
               <ID>string</ID>
               <xsi:type>string</xsi:type>
               <URI>string</URI>
            </Grantee>
            <Permission>string</Permission>
         </Grant>
      </TargetGrants>
      <TargetPrefix>string</TargetPrefix>
   </LoggingEnabled>
</BucketLoggingStatus>
```
- **Purpose**: Enable access logging to a target bucket
- **Note**: Root element is `BucketLoggingStatus` (NOT `LoggingConfiguration`), with `LoggingEnabled` wrapper

### Domain Model
- **Current**: `BucketLoggingConfiguration` value object — exists
- **Integration**: `withLoggingConfiguration(BucketLoggingConfiguration)`
- **Event**: `BucketLoggingConfigurationChanged`
- **Constraints**: Target bucket must exist in same region; no circular logging

### Design Approach

This feature should be designed as an **extensible interface**. Define an `AccessLogWriter` interface — future modules can write to files, Kafka, S3 target bucket, or other destinations.

**Implementation: POSTPONED** — only the interface is designed now.

---

## 5. Website Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?website`
- **Response XML** (from official AWS docs):
```xml
<WebsiteConfiguration>
   <RedirectAllRequestsTo>
      <HostName>string</HostName>
      <Protocol>string</Protocol>
   </RedirectAllRequestsTo>
   <IndexDocument>
      <Suffix>string</Suffix>
   </IndexDocument>
   <ErrorDocument>
      <Key>string</Key>
   </ErrorDocument>
   <RoutingRules>
      <RoutingRule>
         <Condition>
            <HttpErrorCodeReturnedEquals>string</HttpErrorCodeReturnedEquals>
            <KeyPrefixEquals>string</KeyPrefixEquals>
         </Condition>
         <Redirect>
            <HostName>string</HostName>
            <HttpRedirectCode>string</HttpRedirectCode>
            <Protocol>string</Protocol>
            <ReplaceKeyPrefixWith>string</ReplaceKeyPrefixWith>
            <ReplaceKeyWith>string</ReplaceKeyWith>
         </Redirect>
      </RoutingRule>
   </RoutingRules>
</WebsiteConfiguration>
```
- **Purpose**: Static website hosting configuration
- **Note**: `IndexDocument.Suffix`, `ErrorDocument.Key`, `RedirectAllRequestsTo`, `RoutingRules` with Condition+Redirect

### Domain Model
- **Current**: `BucketWebsiteConfiguration` value object — exists
- **Integration**: `withWebsiteConfiguration(BucketWebsiteConfiguration)`
- **Event**: `BucketWebsiteConfigurationChanged`
- **Constraints**: RedirectAllRequestsTo and IndexDocument are mutually exclusive

---

## 6. Notification Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?notification`
- **Response XML** (from official AWS docs):
```xml
<NotificationConfiguration>
   <TopicConfiguration>
      <Event>string</Event> ...
      <Filter>
         <S3Key>
            <FilterRule><Name>string</Name><Value>string</Value></FilterRule> ...
         </S3Key>
      </Filter>
      <Id>string</Id>
      <Topic>string</Topic>
   </TopicConfiguration> ...
   <QueueConfiguration>
      <Event>string</Event> ...
      <Filter><S3Key><FilterRule>...</FilterRule></S3Key></Filter>
      <Id>string</Id>
      <Queue>string</Queue>
   </QueueConfiguration> ...
   <CloudFunctionConfiguration>
      <Event>string</Event> ...
      <Filter><S3Key><FilterRule>...</FilterRule></S3Key></Filter>
      <Id>string</Id>
      <CloudFunction>string</CloudFunction>
   </CloudFunctionConfiguration> ...
</NotificationConfiguration>
```
- **Purpose**: Event notifications for S3 events
- **Note**: AWS uses `CloudFunctionConfiguration` (NOT `LambdaConfiguration`), plus `TopicConfiguration`, `QueueConfiguration`

### Domain Model
- **Current**: `BucketNotificationConfiguration` value object — exists
- **Integration**: `withNotificationConfiguration(BucketNotificationConfiguration)`
- **Event**: `BucketNotificationConfigurationChanged`
- **Constraints**: Multiple destinations for same event type allowed

### Design Approach

This feature should be designed as an **extensible event publishing interface**, not a concrete implementation. The domain should define a `NotificationEventPublisher` interface that supports `TopicConfiguration`, `QueueConfiguration`, and `CloudFunctionConfiguration` as destination types. Future modules can integrate Kafka, AWS SNS/SQS, or other event buses.

**Implementation: POSTPONED** — only the interface is designed now.

---

## 7. Replication Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?replication`
- **Response XML** (from official AWS docs):
```xml
<ReplicationConfiguration>
   <Role>string</Role>
   <Rule>
      <DeleteMarkerReplication><Status>string</Status></DeleteMarkerReplication>
      <Destination>
         <AccessControlTranslation><Owner>string</Owner></AccessControlTranslation>
         <Account>string</Account>
         <Bucket>string</Bucket>
         <EncryptionConfiguration><ReplicaKmsKeyID>string</ReplicaKmsKeyID></EncryptionConfiguration>
         <Metrics>
            <EventThreshold><Minutes>integer</Minutes></EventThreshold>
            <Status>string</Status>
         </Metrics>
         <ReplicationTime>
            <Status>string</Status>
            <Time><Minutes>integer</Minutes></Time>
         </ReplicationTime>
         <StorageClass>string</StorageClass>
      </Destination>
      <ExistingObjectReplication><Status>string</Status></ExistingObjectReplication>
      <Filter><And><Prefix>string</Prefix>...</And><Prefix>string</Prefix></Filter>
      <ID>string</ID>
      <Priority>integer</Priority>
      <SourceSelectionCriteria>...</SourceSelectionCriteria>
      <Status>string</Status>
   </Rule> ...
</ReplicationConfiguration>
```
- **Purpose**: Cross-bucket replication rules
- **Fields**: `Role`, `Rule` (ID, Status, Priority, Filter, Destination, Source selection)
- **Destination**: `Bucket`, `StorageClass`, `Account`, `EncryptionConfiguration`, `AccessControlTranslation`, `Metrics`, `ReplicationTime`

### Domain Model
- **Current**: `BucketReplicationConfiguration` value object — exists
- **Integration**: `withReplicationConfiguration(BucketReplicationConfiguration)`
- **Event**: `BucketReplicationConfigurationChanged`
- **Constraints**: Source/destination buckets must have versioning enabled

---

## 8. Bucket Policy

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?policy`
- **Response**: Raw JSON `{ {{Policy}} in JSON format }`
- **Purpose**: Resource-based IAM policy for the bucket

### Domain Model
- Store as raw `String` (JSON document)
- **Integration**: `withPolicy(String policyJson)`
- **Event**: `BucketPolicyChanged`
- **Constraints**: Valid IAM JSON; size limit (20KB)

---

## 9. PublicAccessBlock Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?publicAccessBlock`
- **Response XML** (from official AWS docs):
```xml
<PublicAccessBlockConfiguration>
   <BlockPublicAcls>boolean</BlockPublicAcls>
   <IgnorePublicAcls>boolean</IgnorePublicAcls>
   <BlockPublicPolicy>boolean</BlockPublicPolicy>
   <RestrictPublicBuckets>boolean</RestrictPublicBuckets>
</PublicAccessBlockConfiguration>
```
- **Purpose**: Block public access to bucket and objects
- **Note**: Field is `RestrictPublicBuckets` (NOT `RestrictPublicBucketPolicy`)

### Domain Model
- **Current**: `PublicAccessBlockConfiguration` value object — exists
- **Integration**: `withPublicAccessBlock(PublicAccessBlockConfiguration)`
- **Event**: `BucketPublicAccessBlockChanged`

---

## 10. Ownership Controls

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?ownershipControls`
- **Response XML** (from official AWS docs):
```xml
<OwnershipControls>
   <Rule>
      <ObjectOwnership>string</ObjectOwnership>
   </Rule> ...
</OwnershipControls>
```
- **Purpose**: Object ownership controls

### Domain Model
- **Current**: `BucketOwnershipControls` value object — exists
- **Integration**: `withOwnershipControls(BucketOwnershipControls)`
- **Event**: `BucketOwnershipControlsChanged`
- **Constraints**: Once set to BucketOwnerEnforced, cannot revert

---

## 11. RequestPayment Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?requestPayment`
- **Response XML** (from official AWS docs):
```xml
<RequestPaymentConfiguration>
   <Payer>string</Payer>
</RequestPaymentConfiguration>
```
- **Purpose**: Controls who pays for the cost of requests (downloads). When set to `Requester`, the requester pays for data transfer costs instead of the bucket owner. Used for sharing data with others who should pay for their own downloads.
- Typically used when: You want to share large datasets and don't want to pay for others' downloads
- Common in: Scientific data sharing, large public datasets
- **Implementation: POSTPONED** — needs request-payer header validation

### Domain Model
- **Current**: `BucketRequestPaymentConfiguration` value object — exists
- **Integration**: `withRequestPaymentConfiguration(BucketRequestPaymentConfiguration)`
- **Event**: `BucketRequestPaymentChanged`

---

## 12. Accelerate Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?accelerate`
- **Response XML** (from official AWS docs):
```xml
<AccelerateConfiguration>
   <Status>string</Status>
</AccelerateConfiguration>
```
- **Purpose**: S3 Transfer Acceleration

### Domain Model
- **Current**: `BucketAccelerateConfiguration` value object — exists
- **Integration**: `withAccelerateConfiguration(BucketAccelerateConfiguration)`
- **Event**: `BucketAccelerateConfigurationChanged`

---

## 13. Analytics Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?analytics&analyticsId={id}` and `GET /{bucket}?analytics&list-type`
- **Response XML** (from official AWS docs):
```xml
<AnalyticsConfiguration>
   <Id>string</Id>
   <Filter>
      <And><Prefix>string</Prefix><Tag><Key>string</Key><Value>string</Value></Tag>...</And>
      <Prefix>string</Prefix>
      <Tag><Key>string</Key><Value>string</Value></Tag>
   </Filter>
   <StorageClassAnalysis>
      <DataExport>
         <Destination>
            <S3BucketDestination>
               <Bucket>string</Bucket>
               <BucketAccountId>string</BucketAccountId>
               <Format>string</Format>
               <Prefix>string</Prefix>
            </S3BucketDestination>
         </Destination>
      </DataExport>
   </StorageClassAnalysis>
</AnalyticsConfiguration>
```
- **Purpose**: Configure analytics exports to a target bucket
- **List API**: Returns `<ListBucketAnalyticsConfigurationResult>` with pagination (`IsTruncated`, `ContinuationToken`)
- **Multi-instance** feature

### Domain Model
- **Current**: `BucketAnalyticsConfiguration` value object — exists
- **Integration**: Map<String, BucketAnalyticsConfiguration>, `addAnalyticsConfiguration()`, `removeAnalyticsConfiguration()`, `listAnalyticsConfigurations()`
- **Event**: `BucketAnalyticsConfigurationAdded`, `BucketAnalyticsConfigurationRemoved`

### Design Approach

This feature should be designed as an **extensible interface**. Define an `AnalyticsDataExport` interface — future modules can export to different destinations (S3 bucket, Kafka, etc.).

**Implementation: POSTPONED** — only the interface is designed now.

---

## 14. Inventory Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?inventory&inventoryId={id}` and `GET /{bucket}?inventory&list-type`
- **Response XML** (from official AWS docs):
```xml
<InventoryConfiguration>
   <Destination>
      <S3BucketDestination>
         <AccountId>string</AccountId>
         <Bucket>string</Bucket>
         <Encryption>
            <SSE-KMS><KeyId>string</KeyId></SSE-KMS>
            <SSE-S3></SSE-S3>
         </Encryption>
         <Format>string</Format>
         <Prefix>string</Prefix>
      </S3BucketDestination>
   </Destination>
   <IsEnabled>boolean</IsEnabled>
   <Filter><Prefix>string</Prefix></Filter>
   <Id>string</Id>
   <IncludedObjectVersions>string</IncludedObjectVersions>
   <OptionalFields><Field>string</Field></OptionalFields>
   <Schedule><Frequency>string</Frequency></Schedule>
</InventoryConfiguration>
```
- **Purpose**: Generates a periodic inventory report of all objects in a bucket. Reports include object metadata (size, ETag, storage class, etc.) and can be configured to run daily or weekly.
- Use cases: Compliance auditing, cost analysis, object tracking
- The inventory is written to a destination bucket as CSV/ORC files
- **Implementation: POSTPONED** — needs scheduled job in storage-engine module
- **Multi-instance** feature with pagination

### Domain Model
- **Current**: `BucketInventoryConfiguration` value object — exists
- **Integration**: Map<String, BucketInventoryConfiguration>, add/remove/list
- **Event**: `BucketInventoryConfigurationAdded`, `BucketInventoryConfigurationRemoved`

---

## 15. Metrics Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?metrics&metricsId={id}` and `GET /{bucket}?metrics&list-type`
- **Response XML** (from official AWS docs):
```xml
<MetricsConfiguration>
   <Id>string</Id>
   <Filter>
      <AccessPointArn>string</AccessPointArn>
      <And>
         <AccessPointArn>string</AccessPointArn>
         <Prefix>string</Prefix>
         <Tag><Key>string</Key><Value>string</Value></Tag> ...
      </And>
      <Prefix>string</Prefix>
      <Tag><Key>string</Key><Value>string</Value></Tag>
   </Filter>
</MetricsConfiguration>
```
- **Purpose**: CloudWatch metrics filter configuration
- **Note**: Filter supports `AccessPointArn` in addition to prefix/tags
- **Multi-instance** feature

### Domain Model
- **Current**: `BucketMetricsConfiguration` value object — exists
- **Integration**: Map<String, BucketMetricsConfiguration>, add/remove/list
- **Event**: `BucketMetricsConfigurationAdded`, `BucketMetricsConfigurationRemoved`

### Design Approach

This feature should be designed as an **extensible interface**. Define a `MetricsCollector` interface — future modules can integrate with CloudWatch, Prometheus, Kafka, or other metrics backends.

**Implementation: POSTPONED** — only the interface is designed now.

---

## 16. Intelligent-Tiering Configuration

### AWS API
- **Endpoint**: `GET/PUT/DELETE /{bucket}?intelligent-tiering`
- **Response XML** (from official AWS docs):
```xml
<IntelligentTieringConfiguration>
   <Id>string</Id>
   <Filter>
      <And><Prefix>string</Prefix><Tag><Key>string</Key><Value>string</Value></Tag>...</And>
      <Prefix>string</Prefix>
      <Tag><Key>string</Key><Value>string</Value></Tag>
   </Filter>
   <Status>string</Status>
   <Tiering>
      <AccessTier>string</AccessTier>
      <Days>integer</Days>
   </Tiering> ...
</IntelligentTieringConfiguration>
```
- **Purpose**: Configure S3 Intelligent-Tiering
- **Note**: Uses `Tiering` (NOT `TieringDefinitions`) with `AccessTier` and `Days`
- **Multi-instance** feature

### Domain Model
- **Current**: `BucketIntelligentTieringConfiguration` value object — exists
- **Integration**: Map<String, BucketIntelligentTieringConfiguration>, add/remove/list
- **Event**: `BucketIntelligentTieringConfigurationAdded`, `BucketIntelligentTieringConfigurationRemoved`

---

## Cross-Cutting Concerns

### Config Type Categorization

| Category | Types | Pattern |
|----------|-------|---------|
| **Singleton** | CORS, Lifecycle, Encryption, Logging, Website, Notification, Replication, Policy, PublicAccessBlock, OwnershipControls, RequestPayment, Accelerate | Single value object, `with*()` / `without*()` methods |
| **Multi-instance** | Analytics, Inventory, Metrics, IntelligentTiering | Map<String, ValueObject>, `add*()` / `remove*()` / `list*()` methods |

### Domain Events

Instead of a single generic `BucketConfigurationChanged`, use specific events:

```
ObjectStorageEvent
├── BucketCreated
├── BucketVersioningEnabled
├── BucketVersioningSuspended
├── BucketEncryptionEnabled        ← already exists (separate from encryption config!)
├── BucketCorsConfigurationChanged
├── BucketLifecycleConfigurationChanged
├── BucketEncryptionConfigurationChanged
├── BucketLoggingConfigurationChanged
├── BucketWebsiteConfigurationChanged
├── BucketNotificationConfigurationChanged
├── BucketReplicationConfigurationChanged
├── BucketPolicyChanged
├── BucketPublicAccessBlockChanged
├── BucketOwnershipControlsChanged
├── BucketRequestPaymentChanged
├── BucketAccelerateConfigurationChanged
├── BucketAnalyticsConfigurationAdded
├── BucketAnalyticsConfigurationRemoved
├── BucketInventoryConfigurationAdded
├── BucketInventoryConfigurationRemoved
├── BucketMetricsConfigurationAdded
├── BucketMetricsConfigurationRemoved
├── BucketIntelligentTieringConfigurationAdded
├── BucketIntelligentTieringConfigurationRemoved
```

### Handler Integration

The **S3BucketConfigHandler** should be refactored into a **registry-based** handler:
1. A `Map<ConfigType, ConfigHandlerStrategy>` registry
2. Each strategy knows how to:
   - Extract config from Bucket aggregate
   - Apply config change to Bucket aggregate (creating proper event)
   - Serialize to Query DTO
   - Deserialize from Command DTO
3. Eliminates all copypasta — each config type is a few lines of strategy registration

### Bucket Aggregate Changes

Bucket should have:
- Dedicated fields for each singleton config (not a flat Configuration record)
- A `Map<String, T>` or similar for multi-instance configs
- `with*()` / `without*()` methods per config type
- No more generic `withConfiguration()` / `Configuration` record

### Implementation Order

| Phase | Config Types | Complexity | Notes |
|-------|-------------|------------|-------|
| 1 | CORS, Accelerate, RequestPayment, OwnershipControls | Simple | Already have value objects; simple XML |
| 2 | Encryption, Logging, Website, PublicAccessBlock | Medium | Richer XML structures |
| 3 | Lifecycle, Notification | Medium-High | Complex nested XML with filters |
| 4 | Replication, Policy | High | Cross-bucket dependencies, JSON policy |
| 5 | Analytics, Inventory, Metrics, IntelligentTiering | Medium | Multi-instance pattern with pagination |

### Key Corrections from AWS Docs

| Config | Old (incorrect) | New (from AWS docs) |
|--------|-----------------|---------------------|
| PublicAccessBlock | `RestrictPublicBucketPolicy` | `RestrictPublicBuckets` |
| Logging root | `LoggingConfiguration` | `BucketLoggingStatus` with `LoggingEnabled` wrapper |
| Encryption root | `EncryptionConfiguration` | `ServerSideEncryptionConfiguration` |
| Notification | `LambdaConfiguration` | `CloudFunctionConfiguration` |
| IntelligentTiering | `TieringDefinitions` | `Tiering` with `AccessTier` + `Days` |
| Lifecycle filter | Simple prefix | Rich `Filter` with `And`, `Tag`, `ObjectSizeGreaterThan/LessThan` |
| Lifecycle expiration | Days/Date only | Also `ExpiredObjectDeleteMarker`, `NewerNoncurrentVersions` |

---

## Open Design Issues

The design document above describes the GET/PUT/DELETE endpoints and the domain model for each bucket configuration feature. However, it does **not** specify what action or behavior occurs **after** the configuration is applied. These are open design issues that must be resolved before the implementation is complete.

### 1. Lifecycle Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | When does the system evaluate lifecycle rules? On a schedule? Immediately on each object write? Does it scan existing objects? |
| **Evaluation trigger** | Is evaluation triggered by object write events, or by a periodic scheduler? |
| **Expiration semantics** | Does expiration delete objects immediately or mark them for deferred deletion? |
| **Transition semantics** | Does transitioning to a storage class actually change storage, or is it a metadata-only change? |
| **Noncurrent version handling** | How does the system know when a version becomes "noncurrent"? |
| **Abort incomplete multipart** | Does the system scan for incomplete multipart uploads on schedule, or only on object write? |

### 2. Notification Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system start delivering events immediately? How is the event bridge wired to the notification destinations? |
| **Event sourcing** | Does the system emit events from domain event records, or from a separate event pipeline? |
| **Destination integration** | How are topic ARNs, queue ARNs, and Lambda function ARNs resolved? Does the system validate them at PUT time or at delivery time? |
| **Filter evaluation** | How are event type filters (e.g., `s3:ObjectCreated:*`) evaluated against real object events? |
| **Delivery guarantees** | Is delivery at-least-once, at-most-once, or best-effort? |

### 3. Replication Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does existing data get replicated immediately? Only new objects? |
| **Replication trigger** | Is replication triggered by object write events, or by a background scanner? |
| **Existing object replication** | Does `ExistingObjectReplication` status matter for backfill? |
| **Destination validation** | Does the system verify the destination bucket exists and is accessible? |
| **Replication time** | How does `ReplicationTime` configuration affect delivery SLA? |
| **Delete marker replication** | How does the system replicate delete markers? |

### 4. Encryption Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Are existing unencrypted objects re-encrypted? Is there a background migration? |
| **Default encryption** | Does the system apply the default encryption to new objects at write time? How? |
| **KMS key resolution** | How is the KMS key resolved? Does the system have an internal KMS mock? |
| **Blocked encryption types** | How does the system reject objects with blocked encryption types? |
| **Bucket key** | What does `BucketKeyEnabled` mean in an in-memory implementation? |

### 5. Logging Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does logging start immediately? How is the target bucket written to? |
| **Log format** | What format are the log records? Does the system generate S3-compatible log files? |
| **Target bucket** | Does the system write logs to the target bucket in real-time, or buffer and flush? |
| **Access log semantics** | What events are logged? All requests? Only writes? Only anonymous requests? |
| **Circular logging prevention** | How does the system detect and prevent circular logging (source logging to a target that logs back)? |

### 6. Website Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system start serving static website content immediately? How is the routing rules engine wired? |
| **Redirect semantics** | Does the system actually redirect HTTP requests, or just return redirect responses? |
| **Index document** | How does the system resolve index document requests? Does it look up `index.html` in the bucket? |
| **Error document** | Does the system serve custom error documents for 404/403? |
| **Routing rules** | How are routing rule conditions evaluated against incoming requests? |

### 7. CORS Configuration

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system start enforcing CORS headers immediately? How is the CORS evaluation wired to the RouterFunction? |
| **Preflight handling** | How does the system handle OPTIONS preflight requests? Does it return proper CORS headers? |
| **Origin validation** | How are allowed origins matched against request `Origin` header? |

### 8. Bucket Policy

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system start enforcing IAM policy evaluation immediately? How is the policy engine wired? |
| **Policy evaluation** | Does the system have an IAM policy evaluation engine? How are `Effect`, `Action`, `Resource`, `Condition` evaluated? |
| **Size limit** | The 20KB size limit is documented, but how is it enforced at PUT time? |

### 9. PublicAccessBlock

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system block public access immediately? How are existing ACLs/policies re-evaluated? |
| **Enforcement** | How does the system reject requests that violate the block configuration? |
| **Interaction with ACL/policy** | How does `BlockPublicAcls` interact with ACL evaluation? Does it override `IgnorePublicAcls`? |

### 10. OwnershipControls

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system change object ownership behavior immediately? How does `ObjectOwnership` affect writes? |
| **Irreversibility** | The constraint "cannot revert BucketOwnerEnforced" is documented, but how is it enforced? |

### 11. Multi-instance Configurations (Analytics, Inventory, Metrics, Intelligent-Tiering)

| Aspect | Open Question |
|--------|---------------|
| **After PUT** | Does the system start exporting/collecting data immediately? When is the first export triggered? |
| **Export schedule** | For Analytics and Inventory, how does the schedule frequency (Daily/Hourly) work? Is there a scheduler? |
| **Destination** | How does the system write export data to the target bucket? What format does it use? |
| **Metrics collection** | How does CloudWatch metrics integration work? Does the system expose a metrics endpoint? |

### Cross-Cutting Open Issues

| Aspect | Open Question |
|--------|---------------|
| **Registry-based handler** | The design calls for a registry of `ConfigHandlerStrategy` per config type. How is this wired? How does the handler discover strategies? |
| **Background processing** | Many config types require background processing (scheduling, scanning, replication). Does the system have a scheduler framework? |
| **Event bridge** | How are domain events routed to notification destinations? Is there an internal event bus? |
| **Persistence** | In-memory persistence means all config is lost on restart. Is this acceptable for the current phase? |

---

## Actions After Configuration — Design

This section specifies what action or behavior occurs **after** each bucket configuration is applied via PUT. It resolves the open design issues listed above by defining concrete runtime actions, required services, and integration points.

### 1. CORS

**After PUT**: CORS rules stored on Bucket. On every GET/PUT/POST request with `Origin` header, the handler checks CORS rules before responding. Already implemented in `S3ObjectOperationsHandler`/`S3BucketOperationsHandler`.

**Action**: Runtime check on each request with `Origin` header.

**Required code**: Already done — CORS evaluation wired in handler layer.

---

### 2. Lifecycle

**After PUT**: Lifecycle rules stored. Need a **scheduled service** that:
- Runs periodically (e.g., every hour or configurable interval)
- Scans all objects in the bucket
- Applies expiration rules (delete objects)
- Applies transition rules (change storage class)
- Applies abort-incomplete-multipart-upload rules

**Action**: Periodic background scan + application of lifecycle actions.

**Required services**:
- `LifecycleEvaluationService` (new) — orchestrates the scan
- `LifecycleRuleEvaluator` (new) — evaluates rules against an object
- Scheduled via `@Scheduled` or reactive scheduler

**Open**: How to handle versioned objects? What about noncurrent versions?

---

### 3. Encryption

**After PUT**: Default encryption configuration stored. When new objects are created via PUT without explicit encryption headers, the system applies the default encryption (SSE-S3, SSE-KMS, or SSE-C).

**Action**: Creation-time default encryption application.

**Required code**:
- Already partially implemented: the `encryptionEnabled` flag on Bucket
- Need to wire: when creating `S3Object`, check bucket encryption config and apply

**Note**: This is a **creation-time** action, not a background job.

---

### 4. Logging

**After PUT**: Access logging enabled. The system should use an **extensible `AccessLogWriter` interface** for writing log entries. A default implementation can write to a target bucket, while future modules can write to files, Kafka, or other destinations.

**Action**: Request intercept + log write via `AccessLogWriter`.

**Required services**:
- `AccessLogWriter` interface (new) — extensible log writing contract
- Default implementation writing to target bucket

**Implementation: POSTPONED** — only the interface is designed now.

---

### 5. Website

**After PUT**: Static website hosting enabled. When a GET request arrives without specific S3 API parameters, the handler checks website config and:
- If `IndexDocument`: serve the index document for root path
- If `ErrorDocument`: serve error document for 404s
- If `RedirectAllRequestsTo`: redirect all requests
- If `RoutingRules`: apply routing rules

**Action**: Request-routing in handler layer.

**Required code**:
- Website request predicate + handler in `S3ProxyRouter`
- Routing rules engine (evaluate conditions, produce redirects)

---

### 6. Notification

**After PUT**: Event notifications enabled. The system should use an **extensible `NotificationEventPublisher` interface** for publishing events to destinations (TopicConfiguration, QueueConfiguration, CloudFunctionConfiguration). A default implementation can deliver to in-memory destinations, while future modules can integrate Kafka, AWS SNS/SQS, or other event buses.

**Action**: Domain event subscription + destination dispatch via `NotificationEventPublisher`.

**Required services**:
- `NotificationEventPublisher` interface (new) — extensible event publishing contract
- Default implementation with in-memory destinations

**Implementation: POSTPONED** — only the interface is designed now.

---

### 7. Replication

**After PUT**: Cross-bucket replication enabled. Need a **ReplicationService** that:
- On object create/delete in source bucket, checks replication rules
- If matching rule, copies object to destination bucket
- Supports `DeleteMarkerReplication`, `ExistingObjectReplication`

**Action**: Event-driven cross-bucket object copy.

**Required services**:
- `ReplicationCoordinator` (new) — coordinates replication rules
- Cross-bucket object copy logic

**Open**: How to replicate existing objects? Only new objects for now?

---

### 8. Policy

**After PUT**: IAM policy stored. Need a **PolicyEvaluationService** that:
- On every request, evaluates the bucket policy
- Checks if the request is allowed or denied

**Action**: Request-time IAM policy evaluation.

**Required services**:
- IAM policy evaluation engine (simplified for now)
- Policy check wired into handler pipeline

**Open**: How to evaluate IAM policies without AWS IAM? Simplified implementation?

---

### 9. PublicAccessBlock

**After PUT**: Public access blocked. On every request, the handler checks:
- If `BlockPublicAcls`: reject PUT acl with public grants
- If `IgnorePublicAcls`: ignore public ACLs
- If `BlockPublicPolicy`: reject public bucket policy
- If `RestrictPublicBuckets`: restrict access to only authorized users

**Action**: Request-time public access check.

**Required code**:
- `PublicAccessBlockEvaluator` in `S3BucketOperationsHandler`
- Wired into ACL/policy evaluation path

---

### 10. OwnershipControls

**After PUT**: Object ownership enforced. On object creation:
- If `BucketOwnerEnforced`: new objects are owned by bucket owner
- If `BucketOwnerPreferred`: bucket owner preferred
- If `ObjectWriter`: the writing account owns the object

**Action**: Creation-time ownership enforcement.

**Required code**:
- Ownership check in object creation handlers
- Ownership field on `S3Object`

---

### 11. RequestPayment

**After PUT**: Requester pays enabled. On every request:
- If `Payer=Requester`, the request must include `x-amz-request-payer` header
- If not, reject with 403

**Action**: Request-time payer validation.

**Required code**:
- Request payer validation in `S3ProxyRouter`
- Reject requests missing the required header

---

### 12. Accelerate

**After PUT**: Transfer acceleration enabled. This requires:
- Alternative endpoint for accelerated transfers
- Different network path for uploads

**Action**: Infrastructure-level network routing change.

**Required infrastructure**:
- Separate port/endpoint for accelerated transfers
- Network-level routing

**Open**: How to implement transfer acceleration without AWS infrastructure? Skip for now?

---

### 13. Analytics

**After PUT**: Analytics export enabled. The system should use an **extensible `AnalyticsDataExport` interface** for exporting analytics data. A default implementation can write to a target S3 bucket, while future modules can export to Kafka or other destinations.

**Action**: Periodic analytics data export via `AnalyticsDataExporter`.

**Required services**:
- `AnalyticsDataExporter` interface (new) — extensible analytics export contract
- Default implementation writing to target bucket

**Implementation: POSTPONED** — only the interface is designed now.

---

### 14. Inventory

**After PUT**: Inventory export enabled. Need a **scheduled service** that:
- Periodically generates inventory of all objects
- Writes to target bucket
- Schedule: daily or weekly

**Action**: Periodic inventory generation and export.

**Required services**:
- `InventoryGenerator` (new) — scans all objects and builds inventory
- Scheduled job

---

### 15. Metrics

**After PUT**: CloudWatch metrics filter configured. The system should use an **extensible `MetricsCollector` interface** for collecting and emitting metrics. A default implementation can store metrics in-memory, while future modules can integrate with CloudWatch, Prometheus, Kafka, or other metrics backends.

**Action**: Filtered metrics collection via `MetricsCollector`.

**Required services**:
- `MetricsCollector` interface (new) — extensible metrics collection contract
- Default implementation with in-memory metrics store

**Implementation: POSTPONED** — only the interface is designed now.

---

### 16. IntelligentTiering

**After PUT**: Intelligent-Tiering configured. Need a **scheduled service** that:
- Monitors object access patterns
- Moves objects between tiers based on access

**Action**: Periodic tiering monitor + object migration between tiers.

**Required services**:
- `TieringMonitor` (new) — tracks access patterns
- Access tracking data store

**Open**: How to track access patterns without real access monitoring?

---

## Priority & Feasibility

This section ranks each configuration action by priority and feasibility, and categorizes what kind of code is needed.

### Priority Definitions

| Priority | Meaning |
|----------|---------|
| P0 | Essential — must work for basic S3 compatibility |
| P1 | Important — needed for full S3 feature parity |
| P2 | Optional — nice to have, deferrable |

### Feasibility Definitions

| Feasibility | Meaning |
|-------------|---------|
| Easy | Minimal code changes, no new services |
| Medium | New service or significant refactoring required |
| Hard | New infrastructure or complex integration required |

### Action Category Definitions

| Category | Meaning |
|----------|---------|
| Handler change | Modification to existing handler classes only |
| New domain code | New domain service or domain event type |
| New infrastructure | New scheduler, filter, network endpoint, or external integration |

### Priority & Feasibility Matrix

| # | Feature | Priority | Feasibility | Code Category | Rationale |
|---|---------|----------|-------------|---------------|----------|
| 1 | CORS | P0 | Easy | Handler change | Already implemented; no new services needed |
| 2 | Lifecycle | P1 | Medium | New domain code + New infrastructure | Requires scheduled scanning service and rule evaluator |
| 3 | Encryption | storage-engine | Medium | New domain code | Default encryption wiring at object creation; moved to storage-engine module (postponed) |
| 4 | Logging | P1 | Medium | New infrastructure | Requires request interception, log formatting, and target bucket writes |
| 5 | Website | P1 | Medium | Handler change | Request routing in handler layer; routing rules engine needed |
| 6 | Notification | P1 | Medium | New domain code | Event bridge + destination adapters; in-memory destinations acceptable for now |
| 7 | Replication | P1 | Hard | New domain code + New infrastructure | Cross-bucket copy; existing object backfill is hard |
| 8 | Policy | P0 | Medium | New domain code | IAM policy evaluation engine needed; simplified implementation acceptable |
| 9 | PublicAccessBlock | P0 | Medium | Handler change | Request-time checks; needs evaluator in handler |
| 10 | OwnershipControls | P0 | Easy | Handler change | Creation-time ownership; minimal code |
| 11 | RequestPayment | P0 | Easy | Handler change | Header validation in router; already simple |
| 12 | Accelerate | P2 | Hard | New infrastructure | Requires separate network endpoint; skip for now |
| 13 | Analytics | P2 | Medium | New domain code | Scheduled export service; deferrable |
| 14 | Inventory | P2 | Medium | New domain code | Scheduled generator; deferrable |
| 15 | Metrics | P2 | Hard | New infrastructure | CloudWatch-like metrics store; in-memory acceptable but non-trivial |
| 16 | IntelligentTiering | P2 | Hard | New domain code + New infrastructure | Access pattern monitoring; complex tracking needed |

### Implementation Priority Order

Based on the priority and feasibility matrix, the recommended implementation order for action-after-configuration is:

| Step | Features | Rationale |
|------|----------|----------|
| 1 | CORS, OwnershipControls, RequestPayment, PublicAccessBlock | P0, Easy/Medium — essential request-time checks, minimal code |
| 2 | Policy | P0, Medium — essential for authorization, simplified engine acceptable |
| 3 | Encryption, Website | P1, Medium — important for creation-time behavior |
| 4 | Lifecycle, Logging, Notification, Replication | P1, Medium/Hard — important but need new services |
| 5 | Accelerate | P2, Hard — defer to later phase |
| 6 | Analytics, Inventory, Metrics, IntelligentTiering | P2, Medium/Hard — defer to final phase |

---

## Extensible Interfaces Design

Several bucket configuration features are **POSTPONED** for implementation. Instead of leaving them undefined, the domain should define **extensible interfaces** that allow future modules to plug in their own implementations. These interfaces live in the `object-storage-domain` module and will be implemented in the `storage-engine` module when its design begins.

### Notification — Event Publishing

```java
// Event publishing for notifications
interface NotificationEventPublisher {
    void publish(String eventType, String bucket, String key, Map<String, String> metadata);
    // Supported destinations: TopicConfiguration, QueueConfiguration, CloudFunctionConfiguration
}
```

### Logging — Access Log Writing

```java
// Access log writing
interface AccessLogWriter {
    void write(AccessLogEntry entry);
}
```

### Metrics — Metrics Collection

```java
// Metrics collection
interface MetricsCollector {
    void record(String metricName, double value, Map<String, String> tags);
}
```

### Analytics — Analytics Data Export

```java
// Analytics data export
interface AnalyticsDataExporter {
    void export(AnalyticsSnapshot snapshot, String destinationBucket);
}
```

> **Note**: These interfaces will be implemented in the `storage-engine` module when its design begins. The domain module only defines the contracts.

---

## Summary

The current codebase has:
- ✅ Correct value objects in domain (17 types)
- ❌ Wrong integration — all stored via generic `Bucket.Configuration` (now reverted to CORS-only)
- ❌ Handler copypasta — 925 lines of identical pattern
- ❌ Generic domain event — loses specific semantics
- ❌ Value object structures don't match actual AWS XML (some fields wrong)
- ❓ **Open design issues** — 11+ config features have unspecified action-after-configuration behavior (see above)

**Fix**: Implement the design above: dedicated Bucket methods, specific events, registry-based handler, proper GET/PUT/DELETE for each config type, with correct AWS XML structures. Then resolve each open design issue before marking the config feature complete.
