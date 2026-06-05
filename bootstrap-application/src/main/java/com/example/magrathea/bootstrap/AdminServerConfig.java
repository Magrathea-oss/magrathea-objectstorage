package com.example.magrathea.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import org.springframework.http.MediaType;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/**
 * Starts a second reactive HTTP server on configurable port (default 8081) serving admin API routes
 * and the Vue.js frontend static files.
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

    @Value("${admin.server.port:8081}")
    private int adminPort;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WebServer adminWebServer() {
        return new AdminNettyWebServer(adminPort);
    }

    /**
     * Lazily-bound Netty web server that starts on the configured port only when
     * Spring calls start() after full context initialization.
     */
    class AdminNettyWebServer implements WebServer {
        private final int port;
        private DisposableServer disposableServer;
        private volatile boolean started = false;

        AdminNettyWebServer(int port) {
            this.port = port;
        }

        @Override
        public void start() {
            if (started) return;
            started = true;

            // Combine admin API route with static file serving
            // NOTE: RouterFunctions.resources("/**", new ClassPathResource("static/")) does NOT work
            // in repackaged JARs because ClassPathResource("static/") is a directory resource.
            // Instead, map each static file/pattern explicitly.
            RouterFunction<ServerResponse> adminRoutes = RouterFunctions
                .route(GET("/admin/health"), req ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new HealthStatus("ok", "Admin API running")))
                .and(RouterFunctions.route(GET("/"), req ->
                    ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(BodyInserters.fromResource(new ClassPathResource("static/index.html")))))
                .and(RouterFunctions.resources("/assets/**", new ClassPathResource("static/assets/")))
                .and(RouterFunctions.route(GET("/favicon.svg"), req ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .body(BodyInserters.fromResource(new ClassPathResource("static/favicon.svg")))))
                .and(RouterFunctions.route(GET("/icons.svg"), req ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .body(BodyInserters.fromResource(new ClassPathResource("static/icons.svg")))));

            HttpHandler httpHandler = RouterFunctions.toHttpHandler(adminRoutes);
            ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);

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
