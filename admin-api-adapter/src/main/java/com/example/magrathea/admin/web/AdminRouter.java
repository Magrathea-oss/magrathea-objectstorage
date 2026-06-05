package com.example.magrathea.admin.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class AdminRouter {

    @Bean
    public RouterFunction<ServerResponse> adminRoutes() {
        return route(GET("/admin/health"), req ->
            ServerResponse.ok().bodyValue(new HealthStatus("ok", "Admin API running")));
    }

    record HealthStatus(String status, String message) {}
}
