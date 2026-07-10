package com.example.magrathea.s3api.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class S3SecurityAuthorizer {

    private final S3SecurityProperties properties;
    private final DurableS3SecurityPolicyStore policyStore;

    public S3SecurityAuthorizer(S3SecurityProperties properties) {
        this(properties, properties.getPolicyFile() == null || properties.getPolicyFile().isBlank()
            ? null
            : new DurableS3SecurityPolicyStore(Path.of(properties.getPolicyFile())));
    }

    public S3SecurityAuthorizer(S3SecurityProperties properties, DurableS3SecurityPolicyStore policyStore) {
        this.properties = properties;
        this.policyStore = policyStore;
    }

    public AuthorizationResult authorize(String principal, ServerHttpRequest request) {
        RequestResource resource = RequestResource.from(request);
        String expectedOwner = request.getHeaders().getFirst("x-amz-expected-bucket-owner");
        S3SecurityProperties.BucketRule bucketRule = properties.bucketRule(resource.bucket());
        if (expectedOwner != null && !expectedOwner.isBlank()
            && bucketRule != null && bucketRule.getOwner() != null
            && !expectedOwner.equals(bucketRule.getOwner())) {
            return AuthorizationResult.denied(resource.action(), resource.bucket(), resource.key(), "expected-owner-mismatch");
        }
        return authorize(principal, resource.action(), resource.bucket(), resource.key());
    }

    public AuthorizationResult authorize(String principal, String action, String bucket, String key) {
        boolean denied = properties.getDenyRules().stream()
            .anyMatch(rule -> rule.matches(principal, action, bucket, key));
        if (!denied) {
            denied = policyRules().stream()
                .filter(DurableS3SecurityPolicyStore.PolicyRule::deny)
                .anyMatch(rule -> rule.matches(principal, action, bucket, key));
        }
        if (denied) {
            return AuthorizationResult.denied(action, bucket, key, "explicit-deny");
        }
        boolean allowed = properties.getAllowRules().stream()
            .anyMatch(rule -> rule.matches(principal, action, bucket, key));
        if (!allowed) {
            allowed = policyRules().stream()
                .filter(DurableS3SecurityPolicyStore.PolicyRule::allow)
                .anyMatch(rule -> rule.matches(principal, action, bucket, key));
        }
        if (allowed) {
            return AuthorizationResult.allowed(action, bucket, key);
        }
        return AuthorizationResult.denied(action, bucket, key, "no-allowing-policy");
    }

    private List<DurableS3SecurityPolicyStore.PolicyRule> policyRules() {
        return policyStore == null ? List.of() : policyStore.rules();
    }

    private record RequestResource(String action, String bucket, String key) {
        static RequestResource from(ServerHttpRequest request) {
            String[] path = Arrays.stream(request.getURI().getRawPath().split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
            String bucket = path.length > 0 ? path[0] : "";
            String key = path.length > 1 ? String.join("/", Arrays.copyOfRange(path, 1, path.length)) : "";
            String action = actionFor(request.getMethod(), request.getURI(), path.length);
            return new RequestResource(action, bucket, key);
        }

        private static String actionFor(HttpMethod method, URI uri, int pathSegmentCount) {
            if (HttpMethod.PUT.equals(method) && pathSegmentCount == 1) {
                return "s3:CreateBucket";
            }
            if (HttpMethod.PUT.equals(method)) {
                return "s3:PutObject";
            }
            if (HttpMethod.GET.equals(method) && pathSegmentCount > 1) {
                return "s3:GetObject";
            }
            if (HttpMethod.HEAD.equals(method) && pathSegmentCount > 1) {
                return "s3:GetObject";
            }
            if (HttpMethod.DELETE.equals(method) && pathSegmentCount > 1) {
                return "s3:DeleteObject";
            }
            if (HttpMethod.GET.equals(method) && pathSegmentCount == 1) {
                return "s3:ListBucket";
            }
            return method.name() + " " + uri.getRawPath();
        }
    }

    public record AuthorizationResult(
        boolean allowed,
        String action,
        String bucket,
        String key,
        String reason
    ) {
        static AuthorizationResult allowed(String action, String bucket, String key) {
            return new AuthorizationResult(true, action, bucket, key, null);
        }

        static AuthorizationResult denied(String action, String bucket, String key, String reason) {
            return new AuthorizationResult(false, action, bucket, key, reason);
        }
    }
}
