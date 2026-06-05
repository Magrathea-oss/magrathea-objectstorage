package com.example.magrathea.admin;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AdminApiApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(AdminApiApplication.class)
            .properties("server.port=${admin.server.port:8081}")
            .run(args);
    }
}
