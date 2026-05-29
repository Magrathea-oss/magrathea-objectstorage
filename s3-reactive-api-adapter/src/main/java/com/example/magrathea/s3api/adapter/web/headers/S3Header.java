package com.example.magrathea.s3api.adapter.web.headers;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comprehensive catalog of all official AWS S3 HTTP headers, divided by category.
 * <p>
 * This enum captures every documented AWS S3 request and response header,
 * organized into meaningful {@link HeaderCategory} groups. It serves as the
 * single source of truth for header handling across all handlers — any handler
 * that needs to read or write an S3 header should reference this enum rather
 * than hardcoding header name strings.
 * </p>
 * <p>
 * Headers prefixed {@code x-amz-meta-*} are user-defined (custom metadata).
 * They are represented here as the category {@link HeaderCategory#USER_METADATA}
 * with the base pattern only — the actual header name is dynamic.
 * </p>
 * <p>
 * Reference: <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/">
 * AWS S3 API Reference</a>
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Check if a header is a known S3 header
 * var known = S3Header.fromHeaderName("x-amz-acl");
 * if (known.isPresent()) {
 *     var category = known.get().category();
 *     // ...
 * }
 *
 * // Get all headers of a specific category
 * var encryptionHeaders = S3Header.headersInCategory(HeaderCategory.SERVER_SIDE_ENCRYPTION);
 * }</pre>
 */
public enum S3Header {

    // ─────────────────────────────────────────────────────────
    //  1. COMMON / STANDARD HTTP HEADERS (S3-relevant)
    // ─────────────────────────────────────────────────────────

    /** Standard HTTP Authorization header — AWS Signature V4 */
    AUTHORIZATION("Authorization", HeaderCategory.COMMON, HeaderDirection.BOTH),
    /** Standard HTTP Content-Length header — body size in bytes */
    CONTENT_LENGTH("Content-Length", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Content-MD5 header — base64-encoded MD5 digest */
    CONTENT_MD5("Content-MD5", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Content-Type header — MIME type of the body */
    CONTENT_TYPE("Content-Type", HeaderCategory.COMMON, HeaderDirection.BOTH),
    /** Standard HTTP Content-Encoding header — encoding applied to the body */
    CONTENT_ENCODING("Content-Encoding", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Content-Disposition header — presentation style */
    CONTENT_DISPOSITION("Content-Disposition", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Content-Language header — language of the content */
    CONTENT_LANGUAGE("Content-Language", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Cache-Control header — caching directives */
    CACHE_CONTROL("Cache-Control", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Expires header — expiration date */
    EXPIRES("Expires", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Expect header — for 100-continue behavior */
    EXPECT("Expect", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Host header — target host */
    HOST("Host", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP Date header — request date */
    DATE("Date", HeaderCategory.COMMON, HeaderDirection.REQUEST),
    /** Standard HTTP ETag header — entity tag for object versioning */
    ETAG("ETag", HeaderCategory.COMMON, HeaderDirection.RESPONSE),

    // ─────────────────────────────────────────────────────────
    //  2. SIGNATURE / AUTHENTICATION (AWS Signature V4)
    // ─────────────────────────────────────────────────────────

    /** x-amz-date — ISO 8601 date used for signature computation */
    X_AMZ_DATE("x-amz-date", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-content-sha256 — SHA256 hash of the body for signing */
    X_AMZ_CONTENT_SHA256("x-amz-content-sha256", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-security-token — session token from AWS STS */
    X_AMZ_SECURITY_TOKEN("x-amz-security-token", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-signature — computed signature (used in query-string auth) */
    X_AMZ_SIGNATURE("x-amz-signature", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-algorithm — algorithm used for signing (used in query-string auth) */
    X_AMZ_ALGORITHM("x-amz-algorithm", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-credential — credential scope (used in query-string auth) */
    X_AMZ_CREDENTIAL("x-amz-credential", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-signed-headers — list of signed headers (used in query-string auth) */
    X_AMZ_SIGNED_HEADERS("x-amz-signed-headers", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),
    /** x-amz-expires — expiration time in seconds (used in pre-signed URLs) */
    X_AMZ_EXPIRES("x-amz-expires", HeaderCategory.SIGNATURE, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  3. ACCESS CONTROL (ACL / Grants)
    // ─────────────────────────────────────────────────────────

    /** x-amz-acl — canned ACL for the bucket or object */
    X_AMZ_ACL("x-amz-acl", HeaderCategory.ACCESS_CONTROL, HeaderDirection.REQUEST),
    /** x-amz-grant-read — grant read access to a specific AWS account */
    X_AMZ_GRANT_READ("x-amz-grant-read", HeaderCategory.ACCESS_CONTROL, HeaderDirection.REQUEST),
    /** x-amz-grant-read-acp — grant read access to the ACL itself */
    X_AMZ_GRANT_READ_ACP("x-amz-grant-read-acp", HeaderCategory.ACCESS_CONTROL, HeaderDirection.REQUEST),
    /** x-amz-grant-write — grant write access */
    X_AMZ_GRANT_WRITE("x-amz-grant-write", HeaderCategory.ACCESS_CONTROL, HeaderDirection.REQUEST),
    /** x-amz-grant-write-acp — grant write access to the ACL itself */
    X_AMZ_GRANT_WRITE_ACP("x-amz-grant-write-acp", HeaderCategory.ACCESS_CONTROL, HeaderDirection.REQUEST),
    /** x-amz-grant-full-control — grant full control (read + write) */
    X_AMZ_GRANT_FULL_CONTROL("x-amz-grant-full-control", HeaderCategory.ACCESS_CONTROL, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  4. SERVER-SIDE ENCRYPTION (SSE)
    // ─────────────────────────────────────────────────────────

    /** x-amz-server-side-encryption — SSE algorithm (AES256, aws:kms, etc.) */
    X_AMZ_SERVER_SIDE_ENCRYPTION("x-amz-server-side-encryption", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),
    /** x-amz-server-side-encryption-aws-kms-key-id — KMS key ID */
    X_AMZ_SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID("x-amz-server-side-encryption-aws-kms-key-id", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),
    /** x-amz-server-side-encryption-context — encryption context (KMS) */
    X_AMZ_SERVER_SIDE_ENCRYPTION_CONTEXT("x-amz-server-side-encryption-context", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),
    /** x-amz-server-side-encryption-customer-algorithm — SSE-C algorithm */
    X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM("x-amz-server-side-encryption-customer-algorithm", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),
    /** x-amz-server-side-encryption-customer-key — SSE-C key (full key) */
    X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY("x-amz-server-side-encryption-customer-key", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.REQUEST),
    /** x-amz-server-side-encryption-customer-key-MD5 — SSE-C key MD5 */
    X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5("x-amz-server-side-encryption-customer-key-MD5", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),
    /** x-amz-server-side-encryption-customer-key-sha256 — SSE-C key SHA256 */
    X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_SHA256("x-amz-server-side-encryption-customer-key-sha256", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),
    /** x-amz-ssekms-key-id — KMS key ID (alternative header) */
    X_AMZ_SSE_KMS_KEY_ID("x-amz-ssekms-key-id", HeaderCategory.SERVER_SIDE_ENCRYPTION, HeaderDirection.BOTH),

    // ─────────────────────────────────────────────────────────
    //  5. CHECKSUM
    // ─────────────────────────────────────────────────────────

    /** x-amz-sdk-checksum-algorithm — SDK-level checksum algorithm */
    X_AMZ_SDK_CHECKSUM_ALGORITHM("x-amz-sdk-checksum-algorithm", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),
    /** x-amz-checksum-crc32 — CRC32 checksum */
    X_AMZ_CHECKSUM_CRC32("x-amz-checksum-crc32", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),
    /** x-amz-checksum-crc32c — CRC32C checksum */
    X_AMZ_CHECKSUM_CRC32C("x-amz-checksum-crc32c", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),
    /** x-amz-checksum-sha1 — SHA1 checksum */
    X_AMZ_CHECKSUM_SHA1("x-amz-checksum-sha1", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),
    /** x-amz-checksum-sha256 — SHA256 checksum */
    X_AMZ_CHECKSUM_SHA256("x-amz-checksum-sha256", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),
    /** x-amz-checksum-crc64nvme — CRC64NVME checksum */
    X_AMZ_CHECKSUM_CRC64NVME("x-amz-checksum-crc64nvme", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),
    /** x-amz-checksum-crc32c-2 — CRC32C variant (CRC32C2) */
    X_AMZ_CHECKSUM_CRC32C_2("x-amz-checksum-crc32c-2", HeaderCategory.CHECKSUM, HeaderDirection.BOTH),

    // ─────────────────────────────────────────────────────────
    //  6. COPY OPERATIONS
    // ─────────────────────────────────────────────────────────

    /** x-amz-copy-source — source object path for CopyObject */
    X_AMZ_COPY_SOURCE("x-amz-copy-source", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-copy-source-range — byte range of source for CopyObject */
    X_AMZ_COPY_SOURCE_RANGE("x-amz-copy-source-range", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-copy-source-version-id — specific version to copy */
    X_AMZ_COPY_SOURCE_VERSION_ID("x-amz-copy-source-version-id", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-copy-source-if-match — copy if source ETag matches */
    X_AMZ_COPY_SOURCE_IF_MATCH("x-amz-copy-source-if-match", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-copy-source-if-none-match — copy if source ETag does NOT match */
    X_AMZ_COPY_SOURCE_IF_NONE_MATCH("x-amz-copy-source-if-none-match", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-copy-source-if-unmodified-since — copy if source unchanged since date */
    X_AMZ_COPY_SOURCE_IF_UNMODIFIED_SINCE("x-amz-copy-source-if-unmodified-since", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-copy-source-if-modified-since — copy if source modified since date */
    X_AMZ_COPY_SOURCE_IF_MODIFIED_SINCE("x-amz-copy-source-if-modified-since", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-metadata-directive — metadata handling during copy (COPY or REPLACE) */
    X_AMZ_METADATA_DIRECTIVE("x-amz-metadata-directive", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-tagging-directive — tagging handling during copy (COPY or REPLACE) */
    X_AMZ_TAGGING_DIRECTIVE("x-amz-tagging-directive", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-object-lock-directive — object lock directive for copy (COPY or REPLACE) */
    X_AMZ_OBJECT_LOCK_DIRECTIVE("x-amz-object-lock-directive", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-object-lock-retain-until-on-directive — retention directive for copy */
    X_AMZ_OBJECT_LOCK_RETAIN_UNTIL_ON_DIRECTIVE("x-amz-object-lock-retain-until-on-directive", HeaderCategory.COPY, HeaderDirection.REQUEST),
    /** x-amz-object-lock-mode-directive — legal hold directive for copy */
    X_AMZ_OBJECT_LOCK_MODE_DIRECTIVE("x-amz-object-lock-mode-directive", HeaderCategory.COPY, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  7. STORAGE CLASS
    // ─────────────────────────────────────────────────────────

    /** x-amz-storage-class — storage class for the object */
    X_AMZ_STORAGE_CLASS("x-amz-storage-class", HeaderCategory.STORAGE_CLASS, HeaderDirection.REQUEST),
    /** x-amz-object-attributes — requested object attributes in GetObjectAttributes */
    X_AMZ_OBJECT_ATTRIBUTES("x-amz-object-attributes", HeaderCategory.STORAGE_CLASS, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  8. VERSIONING
    // ─────────────────────────────────────────────────────────

    /** x-amz-version-id — object version ID (request / response) */
    X_AMZ_VERSION_ID("x-amz-version-id", HeaderCategory.VERSIONING, HeaderDirection.BOTH),
    /** x-amz-delete-marker — indicates object is a delete marker */
    X_AMZ_DELETE_MARKER("x-amz-delete-marker", HeaderCategory.VERSIONING, HeaderDirection.RESPONSE),
    /** x-amz-version-id-marker — marker for version listing pagination */
    X_AMZ_VERSION_ID_MARKER("x-amz-version-id-marker", HeaderCategory.VERSIONING, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  9. TAGGING
    // ─────────────────────────────────────────────────────────

    /** x-amz-tagging — tag set for the object (request) */
    X_AMZ_TAGGING("x-amz-tagging", HeaderCategory.TAGGING, HeaderDirection.REQUEST),
    /** x-amz-tagging-count — number of tags (response) */
    X_AMZ_TAGGING_COUNT("x-amz-tagging-count", HeaderCategory.TAGGING, HeaderDirection.RESPONSE),

    // ─────────────────────────────────────────────────────────
    //  10. OBJECT LOCK / LEGAL HOLD / RETENTION
    // ─────────────────────────────────────────────────────────

    /** x-amz-object-lock-legal-hold — legal hold status (ON | OFF) */
    X_AMZ_OBJECT_LOCK_LEGAL_HOLD("x-amz-object-lock-legal-hold", HeaderCategory.OBJECT_LOCK, HeaderDirection.BOTH),
    /** x-amz-object-lock-mode — lock mode (GOVERNANCE | COMPLIANCE) */
    X_AMZ_OBJECT_LOCK_MODE("x-amz-object-lock-mode", HeaderCategory.OBJECT_LOCK, HeaderDirection.REQUEST),
    /** x-amz-object-lock-retain-until-on — retention period end date */
    X_AMZ_OBJECT_LOCK_RETAIN_UNTIL_ON("x-amz-object-lock-retain-until-on", HeaderCategory.OBJECT_LOCK, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  11. RESTORE / ARCHIVE
    // ─────────────────────────────────────────────────────────

    /** x-amz-restore — restore request or restore status */
    X_AMZ_RESTORE("x-amz-restore", HeaderCategory.RESTORE, HeaderDirection.BOTH),
    /** x-amz-archive-status — archive status (response) */
    X_AMZ_ARCHIVE_STATUS("x-amz-archive-status", HeaderCategory.RESTORE, HeaderDirection.RESPONSE),
    /** x-amz-restore-output-path — output path for restored content */
    X_AMZ_RESTORE_OUTPUT_PATH("x-amz-restore-output-path", HeaderCategory.RESTORE, HeaderDirection.RESPONSE),
    /** x-amz-abort-date — date when the multipart upload was aborted */
    X_AMZ_ABORT_DATE("x-amz-abort-date", HeaderCategory.RESTORE, HeaderDirection.RESPONSE),
    /** x-amz-abort-rule-id — rule ID that triggered the abort */
    X_AMZ_ABORT_RULE_ID("x-amz-abort-rule-id", HeaderCategory.RESTORE, HeaderDirection.RESPONSE),

    // ─────────────────────────────────────────────────────────
    //  12. RENAME
    // ─────────────────────────────────────────────────────────

    /** x-amz-rename-destination — destination key for rename operation */
    X_AMZ_RENAME_DESTINATION("x-amz-rename-destination", HeaderCategory.RENAME, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  13. REQUEST OPTIONS / BILLING
    // ─────────────────────────────────────────────────────────

    /** x-amz-expected-bucket-owner — expected bucket owner account ID */
    X_AMZ_EXPECTED_BUCKET_OWNER("x-amz-expected-bucket-owner", HeaderCategory.REQUEST_OPTIONS, HeaderDirection.REQUEST),
    /** x-amz-request-payer — requester pays indicator */
    X_AMZ_REQUEST_PAYER("x-amz-request-payer", HeaderCategory.REQUEST_OPTIONS, HeaderDirection.BOTH),
    /** x-amz-account-id — account ID for object ownership */
    X_AMZ_ACCOUNT_ID("x-amz-account-id", HeaderCategory.REQUEST_OPTIONS, HeaderDirection.REQUEST),
    /** x-amz-request-charged — indicates request was charged */
    X_AMZ_REQUEST_CHARGED("x-amz-request-charged", HeaderCategory.REQUEST_OPTIONS, HeaderDirection.RESPONSE),
    /** x-amz-request-id — request ID for tracking */
    X_AMZ_REQUEST_ID("x-amz-request-id", HeaderCategory.REQUEST_OPTIONS, HeaderDirection.RESPONSE),
    /** x-amz-id-2 — second request ID (response) */
    X_AMZ_ID_2("x-amz-id-2", HeaderCategory.REQUEST_OPTIONS, HeaderDirection.RESPONSE),

    // ─────────────────────────────────────────────────────────
    //  14. USER METADATA (x-amz-meta-* — custom, dynamic)
    // ─────────────────────────────────────────────────────────

    /**
     * x-amz-meta-{key} — user-defined metadata headers.
     * <p>
     * The actual header name is dynamic: {@code x-amz-meta-<key>}.
     * This entry represents the category only; handlers should match by prefix.
     * </p>
     */
    X_AMZ_META("x-amz-meta-", HeaderCategory.USER_METADATA, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  15. OBJECT OWNERSHIP
    // ─────────────────────────────────────────────────────────

    /** x-amz-object-ownership — object ownership (BucketOwnerEnforced, etc.) */
    X_AMZ_OBJECT_OWNERSHIP("x-amz-object-ownership", HeaderCategory.OBJECT_OWNERSHIP, HeaderDirection.RESPONSE),
    /** x-amz-object-owner — object owner */
    X_AMZ_OBJECT_OWNER("x-amz-object-owner", HeaderCategory.OBJECT_OWNERSHIP, HeaderDirection.RESPONSE),

    // ─────────────────────────────────────────────────────────
    //  16. TRANSFER / NETWORK
    // ─────────────────────────────────────────────────────────

    /** x-amz-transfer-acceleration — transfer acceleration status */
    X_AMZ_TRANSFER_ACCELERATION("x-amz-transfer-acceleration", HeaderCategory.TRANSFER, HeaderDirection.RESPONSE),
    /** x-amz-bucket-region — bucket region (response) */
    X_AMZ_BUCKET_REGION("x-amz-bucket-region", HeaderCategory.TRANSFER, HeaderDirection.RESPONSE),

    // ─────────────────────────────────────────────────────────
    //  17. WEBSITE HOSTING
    // ─────────────────────────────────────────────────────────

    /** x-amz-website-redirect-location — redirect location for website hosting */
    X_AMZ_WEBSITE_REDIRECT_LOCATION("x-amz-website-redirect-location", HeaderCategory.WEBSITE, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  18. SELECT / OBJECT LAMBDA
    // ─────────────────────────────────────────────────────────

    /** x-amz-select-max-bytes — maximum bytes for SelectObjectContent */
    X_AMZ_SELECT_MAX_BYTES("x-amz-select-max-bytes", HeaderCategory.SELECT, HeaderDirection.REQUEST),
    /** x-amz-select-min-progress — minimum progress for SelectObjectContent */
    X_AMZ_SELECT_MIN_PROGRESS("x-amz-select-min-progress", HeaderCategory.SELECT, HeaderDirection.REQUEST),
    /** x-amz-request-transcription — transcription headers for S3 Select */
    X_AMZ_REQUEST_TRANSCRIPTION("x-amz-request-transcription", HeaderCategory.SELECT, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  19. NOTIFICATION / EVENT
    // ─────────────────────────────────────────────────────────

    /** x-amz-notification — event notification configuration */
    X_AMZ_NOTIFICATION("x-amz-notification", HeaderCategory.NOTIFICATION, HeaderDirection.REQUEST),
    /** x-amz-notification-id — notification configuration ID */
    X_AMZ_NOTIFICATION_ID("x-amz-notification-id", HeaderCategory.NOTIFICATION, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  20. INTENT / PHASE F HEADERS
    // ─────────────────────────────────────────────────────────

    /** x-amz-intended-bucket-configuration — intended bucket config (Phase F) */
    X_AMZ_INTENDED_BUCKET_CONFIGURATION("x-amz-intended-bucket-configuration", HeaderCategory.INTENT, HeaderDirection.REQUEST),
    /** x-amz-intended-object-configuration — intended object config (Phase F) */
    X_AMZ_INTENDED_OBJECT_CONFIGURATION("x-amz-intended-object-configuration", HeaderCategory.INTENT, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  21. MULTIPART UPLOAD
    // ─────────────────────────────────────────────────────────

    /** x-amz-part-number — part number (query param, but also header in some cases) */
    X_AMZ_PART_NUMBER("x-amz-part-number", HeaderCategory.MULTIPART, HeaderDirection.REQUEST),
    /** x-amz-upload-id — upload ID (query param, but also header in some cases) */
    X_AMZ_UPLOAD_ID("x-amz-upload-id", HeaderCategory.MULTIPART, HeaderDirection.REQUEST),
    /** x-amz-part-size — part size in bytes */
    X_AMZ_PART_SIZE("x-amz-part-size", HeaderCategory.MULTIPART, HeaderDirection.REQUEST),

    // ─────────────────────────────────────────────────────────
    //  22. BUCKET CONFIGURATION (response hints)
    // ─────────────────────────────────────────────────────────

    /** x-amz-bucket-ownership-enforced — bucket ownership enforced (response) */
    X_AMZ_BUCKET_OWNERSHIP_ENFORCED("x-amz-bucket-ownership-enforced", HeaderCategory.BUCKET, HeaderDirection.RESPONSE),
    /** x-amz-bucket-object-lock-enabled — object lock enabled (response) */
    X_AMZ_BUCKET_OBJECT_LOCK_ENABLED("x-amz-bucket-object-lock-enabled", HeaderCategory.BUCKET, HeaderDirection.RESPONSE),
    ;

    // ─────────────────────────────────────────────────────────
    //  Enum fields
    // ─────────────────────────────────────────────────────────

    private final String headerName;
    private final HeaderCategory category;
    private final HeaderDirection direction;

    S3Header(String headerName, HeaderCategory category, HeaderDirection direction) {
        this.headerName = headerName;
        this.category = category;
        this.direction = direction;
    }

    /** Returns the exact HTTP header name. */
    public String headerName() { return headerName; }

    /** Returns the category this header belongs to. */
    public HeaderCategory category() { return category; }

    /** Returns whether this header is a request header, response header, or both. */
    public HeaderDirection direction() { return direction; }

    // ─────────────────────────────────────────────────────────
    //  Lookup
    // ─────────────────────────────────────────────────────────

    private static final S3Header[] VALUES = values();

    /**
     * Look up an S3Header by its exact HTTP header name (case-insensitive).
     *
     * @param headerName the HTTP header name (e.g. "x-amz-acl")
     * @return the matching S3Header, or empty if not found
     */
    public static java.util.Optional<S3Header> fromHeaderName(String headerName) {
        if (headerName == null) return java.util.Optional.empty();
        // Fast path: check x-amz- prefix
        if (!headerName.startsWith("x-amz-") && !isStandardHeader(headerName)) {
            return java.util.Optional.empty();
        }
        for (var h : VALUES) {
            if (h.headerName.equalsIgnoreCase(headerName)) {
                return java.util.Optional.of(h);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Returns all headers belonging to a given category.
     *
     * @param category the category to filter by
     * @return a set of S3Header values in that category
     */
    public static Set<S3Header> headersInCategory(HeaderCategory category) {
        return java.util.Arrays.stream(VALUES)
            .filter(h -> h.category == category)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns all header names for a given category as strings.
     *
     * @param category the category to filter by
     * @return a set of header name strings
     */
    public static Set<String> headerNamesInCategory(HeaderCategory category) {
        return headersInCategory(category).stream()
            .map(S3Header::headerName)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns all request-direction headers.
     */
    public static Set<S3Header> allRequestHeaders() {
        return java.util.Arrays.stream(VALUES)
            .filter(h -> h.direction == HeaderDirection.REQUEST || h.direction == HeaderDirection.BOTH)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns all response-direction headers.
     */
    public static Set<S3Header> allResponseHeaders() {
        return java.util.Arrays.stream(VALUES)
            .filter(h -> h.direction == HeaderDirection.RESPONSE || h.direction == HeaderDirection.BOTH)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Checks if a header name matches the {@code x-amz-meta-*} pattern.
     *
     * @param headerName the HTTP header name
     * @return true if it starts with "x-amz-meta-"
     */
    public static boolean isUserMetadata(String headerName) {
        return headerName != null && headerName.toLowerCase(java.util.Locale.ROOT).startsWith("x-amz-meta-");
    }

    /**
     * Checks if a header name matches the {@code x-amz-checksum-*} pattern.
     *
     * @param headerName the HTTP header name
     * @return true if it starts with "x-amz-checksum-"
     */
    public static boolean isChecksumHeader(String headerName) {
        return headerName != null && headerName.toLowerCase(java.util.Locale.ROOT).startsWith("x-amz-checksum-");
    }

    private static boolean isStandardHeader(String name) {
        if (name == null) return false;
        var lower = name.toLowerCase(java.util.Locale.ROOT);
        return "authorization".equals(lower)
            || "content-length".equals(lower)
            || "content-md5".equals(lower)
            || "content-type".equals(lower)
            || "content-encoding".equals(lower)
            || "content-disposition".equals(lower)
            || "content-language".equals(lower)
            || "cache-control".equals(lower)
            || "expires".equals(lower)
            || "expect".equals(lower)
            || "host".equals(lower)
            || "date".equals(lower)
            || "etag".equals(lower);
    }

    // ─────────────────────────────────────────────────────────
    //  HeaderCategory enum
    // ─────────────────────────────────────────────────────────

    /**
     * Categories of AWS S3 HTTP headers.
     */
    public enum HeaderCategory {
        /** Common / standard HTTP headers relevant to S3 */
        COMMON,
        /** AWS Signature V4 authentication headers */
        SIGNATURE,
        /** Access control (ACL and grant) headers */
        ACCESS_CONTROL,
        /** Server-side encryption headers (SSE-S3, SSE-KMS, SSE-C) */
        SERVER_SIDE_ENCRYPTION,
        /** Checksum headers (SDK checksum algorithm and per-algorithm checksums) */
        CHECKSUM,
        /** Copy operation headers (copy source, directives, conditional) */
        COPY,
        /** Storage class and object attribute headers */
        STORAGE_CLASS,
        /** Versioning headers (version ID, delete marker) */
        VERSIONING,
        /** Tagging headers */
        TAGGING,
        /** Object lock, legal hold, and retention headers */
        OBJECT_LOCK,
        /** Restore and archive status headers */
        RESTORE,
        /** Rename operation headers */
        RENAME,
        /** Request options, billing, and tracking headers */
        REQUEST_OPTIONS,
        /** User-defined metadata headers (x-amz-meta-*) */
        USER_METADATA,
        /** Object ownership headers (response) */
        OBJECT_OWNERSHIP,
        /** Transfer acceleration and network headers */
        TRANSFER,
        /** Website hosting redirect headers */
        WEBSITE,
        /** S3 Select and Object Lambda headers */
        SELECT,
        /** Event notification headers */
        NOTIFICATION,
        /** Intent / Phase F headers */
        INTENT,
        /** Multipart upload headers */
        MULTIPART,
        /** Bucket configuration response headers */
        BUCKET,
        ;

        /**
         * Returns a human-readable description of this category.
         */
        public String description() {
            return switch (this) {
                case COMMON -> "Common / standard HTTP headers relevant to S3";
                case SIGNATURE -> "AWS Signature V4 authentication headers";
                case ACCESS_CONTROL -> "Access control (ACL and grant) headers";
                case SERVER_SIDE_ENCRYPTION -> "Server-side encryption headers (SSE-S3, SSE-KMS, SSE-C)";
                case CHECKSUM -> "Checksum headers (SDK algorithm + per-algorithm checksums)";
                case COPY -> "Copy operation headers (source, directives, conditional)";
                case STORAGE_CLASS -> "Storage class and object attribute headers";
                case VERSIONING -> "Versioning headers (version ID, delete marker)";
                case TAGGING -> "Tagging headers";
                case OBJECT_LOCK -> "Object lock, legal hold, and retention headers";
                case RESTORE -> "Restore and archive status headers";
                case RENAME -> "Rename operation headers";
                case REQUEST_OPTIONS -> "Request options, billing, and tracking headers";
                case USER_METADATA -> "User-defined metadata headers (x-amz-meta-*)";
                case OBJECT_OWNERSHIP -> "Object ownership headers (response)";
                case TRANSFER -> "Transfer acceleration and network headers";
                case WEBSITE -> "Website hosting redirect headers";
                case SELECT -> "S3 Select and Object Lambda headers";
                case NOTIFICATION -> "Event notification headers";
                case INTENT -> "Intent / Phase F headers";
                case MULTIPART -> "Multipart upload headers";
                case BUCKET -> "Bucket configuration response headers";
            };
        }
    }

    // ─────────────────────────────────────────────────────────
    //  HeaderDirection enum
    // ─────────────────────────────────────────────────────────

    /**
     * Indicates whether a header is a request header, a response header, or both.
     */
    public enum HeaderDirection {
        /** Sent by the client in the request */
        REQUEST,
        /** Sent by the server in the response */
        RESPONSE,
        /** Can appear in both request and response */
        BOTH,
        ;
    }
}
