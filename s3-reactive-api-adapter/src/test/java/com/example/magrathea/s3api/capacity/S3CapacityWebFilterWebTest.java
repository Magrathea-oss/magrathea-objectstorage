package com.example.magrathea.s3api.capacity;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.handler.FilteringWebHandler;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

class S3CapacityWebFilterWebTest {

    @Test
    void rejectsDeclaredAndUnknownLengthPutBodiesAtTheStreamingBoundary() {
        var properties = new S3CapacityProperties();
        properties.setMaxSinglePutBytes(4);
        properties.setRateLimitBurst(20);
        var filter = new S3CapacityWebFilter(properties,
            new S3CapacityMetrics(new SimpleMeterRegistry()));
        var router = RouterFunctions.route()
            .PUT("/{bucket}/{*key}", request -> request.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                .then(ServerResponse.noContent().build()))
            .build();
        var client = WebTestClient.bindToWebHandler(
            new FilteringWebHandler(RouterFunctions.toWebHandler(router), java.util.List.of(filter))).build();

        client.put().uri("/bucket/known.bin").header("Content-Length", "5")
            .bodyValue("12345").exchange()
            .expectStatus().isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
            .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                .contains("EntityTooLarge"));

        var factory = new DefaultDataBufferFactory();
        Flux<DataBuffer> chunks = Flux.just(factory.wrap("123".getBytes(StandardCharsets.UTF_8)),
            factory.wrap("45".getBytes(StandardCharsets.UTF_8)));
        client.put().uri("/bucket/stream.bin")
            .body(BodyInserters.fromDataBuffers(chunks)).exchange()
            .expectStatus().isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
            .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                .contains("EntityTooLarge"));
    }
}
