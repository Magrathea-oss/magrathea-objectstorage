package com.example.magrathea.bootstrap;

import org.springframework.boot.web.server.WebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Starts a second reactive HTTP server on port 8081 serving admin API routes.
 * This allows the admin API to run alongside the main S3 API on port 8080
 * within the same bootstrap-application JAR.
 * 
 * Lifecycle is managed explicitly via initMethod="start" and destroyMethod="stop"
 * so Spring starts the server after the application context is fully initialized.
 */
@Configuration
public class AdminServerConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WebServer adminWebServer() {
        return new AdminNettyWebServer();
    }

    /**
     * Lazily-bound Netty web server that starts on port 8081 only when
     * Spring calls start() after full context initialization.
     */
    class AdminNettyWebServer implements WebServer {
        private DisposableServer disposableServer;
        private volatile boolean started = false;

        @Override
        public void start() {
            if (started) return;
            started = true;

            RouterFunction<ServerResponse> adminRoutes = route(GET("/admin/health"), req ->
                ServerResponse.ok().bodyValue(new HealthStatus("ok", "Admin API running")));

            HttpHandler httpHandler = RouterFunctions.toHttpHandler(adminRoutes);
            ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);

            disposableServer = HttpServer.create()
                .port(8081)
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
            return 8081;
        }
    }

    record HealthStatus(String status, String message) {}
}
