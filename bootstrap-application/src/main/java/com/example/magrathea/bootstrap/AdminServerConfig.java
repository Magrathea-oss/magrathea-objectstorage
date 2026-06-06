package com.example.magrathea.bootstrap;

import com.example.magrathea.admin.web.AdminRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Starts a second reactive HTTP server on configurable port (default 8081) serving admin API routes
 * (from {@link AdminRouter}) and the Vue.js frontend static files.
 * This allows the admin API + frontend to run alongside the main S3 API on port 8080
 * within the same bootstrap-application JAR.
 * 
 * Lifecycle is managed explicitly via initMethod="start" and destroyMethod="stop"
 * so Spring starts the server after the application context is fully initialized.
 * 
 * Port is configurable via the {@code admin.server.port} property (default: 8081).
 */
@Configuration
public class AdminServerConfig {

    private final AdminRouter adminRouter;

    public AdminServerConfig(AdminRouter adminRouter) {
        this.adminRouter = adminRouter;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WebServer adminWebServer(
            @Value("${admin.server.port:8081}") int adminPort) {

        // Combine admin routes from AdminRouter with static resource routes
        RouterFunction<ServerResponse> router = adminRouter.adminRoutes()
            .and(route(GET("/"), req ->
                ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(BodyInserters.fromResource(new ClassPathResource("static/index.html")))))
            .and(RouterFunctions.resources("/assets/**", new ClassPathResource("static/assets/")))
            .and(route(GET("/favicon.svg"), req ->
                ServerResponse.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(BodyInserters.fromResource(new ClassPathResource("static/favicon.svg")))))
            .and(route(GET("/icons.svg"), req ->
                ServerResponse.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(BodyInserters.fromResource(new ClassPathResource("static/icons.svg")))))
            .and(RouterFunctions.resources("/docs/**", new ClassPathResource("static/docs/")));

        HttpHandler httpHandler = RouterFunctions.toHttpHandler(router);
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        return new AdminNettyWebServer(adminPort, adapter);
    }

    /**
     * Netty web server that starts on the configured port only when
     * Spring calls start() after full context initialization.
     * The HttpHandler is provided at construction time from the combined router.
     */
    static class AdminNettyWebServer implements WebServer {
        private final int port;
        private final ReactorHttpHandlerAdapter adapter;
        private DisposableServer disposableServer;
        private volatile boolean started = false;

        AdminNettyWebServer(int port, ReactorHttpHandlerAdapter adapter) {
            this.port = port;
            this.adapter = adapter;
        }

        @Override
        public void start() {
            if (started) return;
            started = true;

            disposableServer = HttpServer.create()
                .port(port)
                .handle(adapter)
                .bindNow();
        }

        @Override
        public void stop() {
            if (disposableServer != null) {
                disposableServer.dispose();
            }
        }

        @Override
        public int getPort() {
            return port;
        }
    }

    record HealthStatus(String status, String message) {}
}
