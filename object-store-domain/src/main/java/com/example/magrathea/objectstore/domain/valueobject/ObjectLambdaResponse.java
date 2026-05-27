package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * ObjectLambdaResponse — a value object representing an Object Lambda write response.
 * <p>
 * Domain-level representation of a {@code WriteGetObjectResponse} call result.
 * Tracks the status, request route, and any error details for the Object Lambda
 * response writing operation.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record ObjectLambdaResponse(
    String status,
    String requestRoute,
    String requestToken,
    String errorMessage
) {

    public ObjectLambdaResponse {
        Objects.requireNonNull(status);
        if (status.isBlank()) throw new IllegalArgumentException("status must not be blank");
        // requestRoute, requestToken, errorMessage may be null (optional)
    }

    /**
     * Factory method — successful response.
     */
    public static ObjectLambdaResponse success(String requestRoute, String requestToken) {
        Objects.requireNonNull(requestRoute);
        Objects.requireNonNull(requestToken);
        return new ObjectLambdaResponse("success", requestRoute, requestToken, null);
    }

    /**
     * Factory method — error response.
     */
    public static ObjectLambdaResponse error(String requestRoute, String requestToken, String errorMessage) {
        Objects.requireNonNull(requestRoute);
        Objects.requireNonNull(requestToken);
        Objects.requireNonNull(errorMessage);
        return new ObjectLambdaResponse("error", requestRoute, requestToken, errorMessage);
    }

    /**
     * Factory method — restore from persistence.
     */
    public static ObjectLambdaResponse restore(String status, String requestRoute, String requestToken,
                                                String errorMessage) {
        return new ObjectLambdaResponse(status, requestRoute, requestToken, errorMessage);
    }

    public boolean isSuccess() {
        return "success".equals(status);
    }

    public boolean isError() {
        return "error".equals(status);
    }
}
