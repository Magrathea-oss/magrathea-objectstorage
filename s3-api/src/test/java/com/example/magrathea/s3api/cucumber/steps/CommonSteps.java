package com.example.magrathea.s3api.cucumber.steps;

import org.springframework.http.HttpStatusCode;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared state between step classes.
 * Registered as a Spring bean by ObjectStorageTestApp — not a Cucumber glue class.
 */
public class CommonSteps {

    private HttpStatusCode responseStatus;
    private String responseBody;
    private final Map<String, String> sharedState = new HashMap<>();

    public void setResponseStatus(HttpStatusCode status) {
        this.responseStatus = status;
    }

    public void setResponseStatus(int statusCode) {
        this.responseStatus = HttpStatusCode.valueOf(statusCode);
    }

    public HttpStatusCode getResponseStatus() {
        return responseStatus;
    }

    public void setResponseBody(String body) {
        this.responseBody = body;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void set(String key, String value) {
        sharedState.put(key, value);
    }

    public String getString(String key) {
        return sharedState.get(key);
    }

    public void reset() {
        this.responseStatus = null;
        this.responseBody = null;
        sharedState.clear();
    }
}
