package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.s3api.cucumber.ObjectStoreTestApp;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ObjectStoreTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@CucumberContextConfiguration
public class ObjectStoreStepsCucumberConfig {
}
