package com.example.magrathea.bootstrap.ep10;

import com.example.magrathea.bootstrap.MagratheaApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/** Test-classpath Spring Boot entry point for the EP-10 real-process acceptance cluster. */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
})
@ComponentScan(
        basePackages = {
                "com.example.magrathea.bootstrap",
                "com.example.magrathea.objectstore",
                "com.example.magrathea.objectstorage",
                "com.example.magrathea.reactive",
                "com.example.magrathea.storageengine",
                "com.example.magrathea.admin"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MagratheaApplication.class))
@Import(Ep10AcceptanceControlConfiguration.class)
public class Ep10AcceptanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(Ep10AcceptanceApplication.class, args);
    }
}
