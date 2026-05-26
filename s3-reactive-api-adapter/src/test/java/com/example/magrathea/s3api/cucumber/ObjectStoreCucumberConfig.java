package com.example.magrathea.s3api.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    classes = ObjectStoreTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@CucumberContextConfiguration
public class ObjectStoreCucumberConfig {
}
