package com.example.magrathea.s3api.awscli;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal Spring Boot application for AWS CLI Cucumber tests.
 *
 * Unlike ObjectStoreTestApp, this context does not bind WebTestClient directly to
 * the RouterFunction. SpringBootTest(RANDOM_PORT) starts a real Netty HTTP server
 * so the external AWS CLI process can connect to the S3-compatible API.
 */
@SpringBootApplication
@ComponentScan({
    "com.example.magrathea.objectstore",
    "com.example.magrathea.reactive"
})
public class AwsCliTestApp {
}
