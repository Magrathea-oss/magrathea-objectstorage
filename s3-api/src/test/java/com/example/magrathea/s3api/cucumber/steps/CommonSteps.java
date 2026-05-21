package com.example.magrathea.s3api.cucumber.steps;

import org.springframework.http.HttpStatusCode;

/**
 * Shared state between step classes.
 * Registered as a Spring bean by ObjectStorageTestApp — not a Cucumber glue class.
 */
public class CommonSteps {

    private HttpStatusCode responseStatus;

    public void setResponseStatus(HttpStatusCode status) {
        this.responseStatus = status;
    }

    public HttpStatusCode getResponseStatus() {
        return responseStatus;
    }
}
