package com.example.magrathea.s3api.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class S3SigV4Verifier {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC);

    private final S3SecurityProperties properties;
    private final S3CredentialStore credentialStore;
    private final Clock clock;

    public S3SigV4Verifier(S3SecurityProperties properties, S3CredentialStore credentialStore) {
        this(properties, credentialStore, Clock.systemUTC());
    }

    S3SigV4Verifier(S3SecurityProperties properties, S3CredentialStore credentialStore, Clock clock) {
        this.properties = properties;
        this.credentialStore = credentialStore;
        this.clock = clock;
    }

    public VerificationResult verify(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return VerificationResult.denied("AccessDenied", "Anonymous requests are not allowed in secured S3 mode");
        }
        if (!authorization.startsWith(ALGORITHM + " ")) {
            return VerificationResult.denied("InvalidArgument", "Unsupported authorization algorithm");
        }

        Map<String, String> auth = parseAuthorization(authorization.substring((ALGORITHM + " ").length()));
        String credential = auth.get("Credential");
        String signedHeaders = auth.get("SignedHeaders");
        String signature = auth.get("Signature");
        if (credential == null || signedHeaders == null || signature == null) {
            return VerificationResult.denied("AuthorizationHeaderMalformed", "Missing SigV4 authorization field");
        }

        CredentialScope scope = CredentialScope.parse(credential);
        if (scope == null) {
            return VerificationResult.denied("AuthorizationHeaderMalformed", "Invalid credential scope");
        }
        if (!properties.getRegion().equals(scope.region())) {
            return VerificationResult.denied("AuthorizationHeaderMalformed", "Credential scope region does not match server region");
        }
        if (!"s3".equals(scope.service()) || !"aws4_request".equals(scope.terminal())) {
            return VerificationResult.denied("AuthorizationHeaderMalformed", "Credential scope must target s3/aws4_request");
        }

        Optional<S3CredentialStore.Credential> credentialEntry = credentialStore.findByAccessKey(scope.accessKey());
        if (credentialEntry.isEmpty()) {
            return VerificationResult.denied("InvalidAccessKeyId", "The AWS access key Id you provided does not exist in our records");
        }

        String amzDate = request.getHeaders().getFirst("X-Amz-Date");
        if (amzDate == null || amzDate.isBlank()) {
            return VerificationResult.denied("AccessDenied", "Missing X-Amz-Date header");
        }
        Instant requestTime;
        try {
            requestTime = AMZ_DATE.parse(amzDate, Instant::from);
        } catch (DateTimeParseException ex) {
            return VerificationResult.denied("AuthorizationHeaderMalformed", "Invalid X-Amz-Date header");
        }
        long skew = Math.abs(Duration.between(Instant.now(clock), requestTime).toSeconds());
        if (skew > properties.getAllowedClockSkewSeconds()) {
            return VerificationResult.denied("RequestTimeTooSkewed", "The difference between the request time and the current time is too large");
        }

        String payloadHash = request.getHeaders().getFirst("X-Amz-Content-SHA256");
        if (payloadHash == null || payloadHash.isBlank()) {
            payloadHash = "UNSIGNED-PAYLOAD";
        }
        // Streaming body replay is intentionally not implemented in this first slice; exact payload
        // hashes are accepted as a signed header value and will be body-verified in the next EP-1 increment.
        // Bad signatures caused by tampering with this value are rejected by the signature check below.

        List<String> signedHeaderNames = Arrays.stream(signedHeaders.split(";"))
            .map(s -> s.toLowerCase(Locale.ROOT).trim())
            .filter(s -> !s.isBlank())
            .toList();
        if (signedHeaderNames.isEmpty()) {
            return VerificationResult.denied("AuthorizationHeaderMalformed", "SignedHeaders is empty");
        }
        for (String headerName : signedHeaderNames) {
            if (request.getHeaders().getFirst(headerName) == null && !"host".equals(headerName)) {
                return VerificationResult.denied("SignatureDoesNotMatch", "Signed header is missing: " + headerName);
            }
        }

        String canonicalRequest = canonicalRequest(request, signedHeaderNames, signedHeaders, payloadHash);
        String stringToSign = String.join("\n",
            ALGORITHM,
            amzDate,
            scope.date() + "/" + scope.region() + "/" + scope.service() + "/" + scope.terminal(),
            sha256Hex(canonicalRequest));
        byte[] signingKey = signingKey(credentialEntry.get().secretKey(), scope.date(), scope.region(), scope.service());
        String expectedSignature = hmacHex(signingKey, stringToSign);
        if (!constantTimeEquals(expectedSignature, signature)) {
            return VerificationResult.denied("SignatureDoesNotMatch", "The request signature we calculated does not match the signature you provided");
        }

        return VerificationResult.allowed(credentialEntry.get().principal());
    }

    private static Map<String, String> parseAuthorization(String value) {
        Map<String, String> fields = new HashMap<>();
        for (String part : value.split(",")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                fields.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
            }
        }
        return fields;
    }

    private static String canonicalRequest(ServerHttpRequest request, List<String> signedHeaderNames,
                                           String signedHeaders, String payloadHash) {
        return String.join("\n",
            request.getMethod().name(),
            canonicalUri(request.getURI()),
            canonicalQueryString(request.getQueryParams()),
            canonicalHeaders(request, signedHeaderNames),
            signedHeaders,
            payloadHash);
    }

    private static String canonicalUri(URI uri) {
        String rawPath = uri.getRawPath();
        return rawPath == null || rawPath.isBlank() ? "/" : rawPath;
    }

    private static String canonicalQueryString(MultiValueMap<String, String> params) {
        List<String> pairs = new ArrayList<>();
        params.forEach((key, values) -> {
            if (values == null || values.isEmpty()) {
                pairs.add(encode(key) + "=");
            } else {
                values.forEach(value -> pairs.add(encode(key) + "=" + encode(value == null ? "" : value)));
            }
        });
        return pairs.stream().sorted().collect(Collectors.joining("&"));
    }

    private static String canonicalHeaders(ServerHttpRequest request, List<String> signedHeaderNames) {
        HttpHeaders headers = request.getHeaders();
        return signedHeaderNames.stream()
            .sorted(Comparator.naturalOrder())
            .map(name -> name + ":" + normalizeHeaderValue(headerValue(headers, request, name)) + "\n")
            .collect(Collectors.joining());
    }

    private static String headerValue(HttpHeaders headers, ServerHttpRequest request, String lowerName) {
        if ("host".equals(lowerName)) {
            String host = headers.getFirst(HttpHeaders.HOST);
            if (host != null && !host.isBlank()) {
                return host;
            }
            return request.getURI().getAuthority();
        }
        for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
            if (entry.getKey().equalsIgnoreCase(lowerName)) {
                return String.join(",", entry.getValue());
            }
        }
        return "";
    }

    private static String normalizeHeaderValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%7E", "~");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] signingKey(String secretKey, String date, String region, String service) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        return hmac(kService, "aws4_request");
    }

    private static String hmacHex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmac(key, data));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private record CredentialScope(String accessKey, String date, String region, String service, String terminal) {
        static CredentialScope parse(String credential) {
            String[] parts = credential.split("/");
            if (parts.length != 5) {
                return null;
            }
            try {
                LocalDate.parse(parts[1], DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException e) {
                return null;
            }
            return new CredentialScope(parts[0], parts[1], parts[2], parts[3], parts[4]);
        }
    }

    public record VerificationResult(boolean allowed, String principal, String errorCode, String message) {
        static VerificationResult allowed(String principal) {
            return new VerificationResult(true, principal, null, null);
        }

        static VerificationResult denied(String errorCode, String message) {
            return new VerificationResult(false, null, errorCode, message);
        }
    }
}
