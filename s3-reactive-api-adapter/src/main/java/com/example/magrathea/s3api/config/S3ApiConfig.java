package com.example.magrathea.s3api.config;

import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveMultipartUploadService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.adapter.web.S3BucketConfigHandler;
import com.example.magrathea.s3api.adapter.web.S3BucketMetadataHandler;
import com.example.magrathea.s3api.adapter.web.S3BucketOperationsHandler;
import com.example.magrathea.s3api.adapter.web.S3MultipartHandler;
import com.example.magrathea.s3api.adapter.web.S3MultipartPartStore;
import com.example.magrathea.s3api.adapter.web.S3ObjectAclStore;
import com.example.magrathea.s3api.adapter.web.S3ObjectMetadataHandler;
import com.example.magrathea.s3api.adapter.web.S3ObjectOperationsHandler;
import com.example.magrathea.s3api.adapter.web.S3PathRouter;
import com.example.magrathea.s3api.adapter.web.S3SessionHandler;
import com.example.magrathea.s3api.capacity.S3CapacityMetrics;
import com.example.magrathea.s3api.capacity.S3CapacityProperties;
import com.example.magrathea.s3api.capacity.S3CapacityWebFilter;
import com.example.magrathea.s3api.capacity.S3ConnectionTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.netty.ConnectionObserver;

/**
 * Auto-configuration for S3 Reactive API Adapter module.
 * Activates when:
 *  - object-store-reactive-application is on the classpath (ReactiveBucketService, ReactiveObjectService available)
 *  - s3.api.enabled property is not false
 *
 * When s3-reactive-api-adapter module is excluded from the build, this config is not loaded.
 * When included but disabled via property, beans are not created.
 */
@AutoConfiguration
@ConditionalOnClass({ ReactiveBucketService.class, ReactiveObjectService.class })
@ConditionalOnProperty(name = "s3.api.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(S3CapacityProperties.class)
public class S3ApiConfig {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry s3CapacityMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public S3CapacityMetrics s3CapacityMetrics(MeterRegistry meterRegistry) {
        return new S3CapacityMetrics(meterRegistry);
    }

    @Bean
    public S3CapacityWebFilter s3CapacityWebFilter(S3CapacityProperties properties,
                                                   S3CapacityMetrics metrics) {
        return new S3CapacityWebFilter(properties, metrics);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.capacity.enabled", havingValue = "true", matchIfMissing = true)
    public S3ConnectionTracker s3ConnectionTracker(S3CapacityProperties properties,
                                                   MeterRegistry meterRegistry) {
        return new S3ConnectionTracker(properties, meterRegistry);
    }

    /**
     * Customizes only Spring Boot's main reactive server. The independently constructed Admin
     * {@code HttpServer} does not use this factory and therefore retains its own connection capacity.
     */
    @Bean
    @ConditionalOnProperty(name = "s3.capacity.enabled", havingValue = "true", matchIfMissing = true)
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> s3ConnectionCapCustomizer(
            S3ConnectionTracker tracker) {
        return factory -> factory.addServerCustomizers(httpServer ->
            httpServer.childObserve((connection, state) -> {
                if (state == ConnectionObserver.State.CONNECTED) {
                    tracker.connected(connection);
                }
            }));
    }

    @Bean
    public S3BucketOperationsHandler s3BucketOperationsHandler(ReactiveBucketService bucketService,
                                                               ReactiveObjectService objectService) {
        return new S3BucketOperationsHandler(bucketService, objectService);
    }

    @Bean
    public S3BucketMetadataHandler s3BucketMetadataHandler(ReactiveBucketService bucketService) {
        return new S3BucketMetadataHandler(bucketService);
    }

    @Bean
    public S3ObjectOperationsHandler s3ObjectOperationsHandler(ReactiveObjectService objectService,
                                                               S3CapacityProperties capacityProperties) {
        return new S3ObjectOperationsHandler(objectService, capacityProperties);
    }

    @Bean
    public S3ObjectAclStore s3ObjectAclStore(
            @Value("${storage.engine.filesystem.root:${java.io.tmpdir}/magrathea-objectstorage/storage-engine}") String storageRoot) {
        return new S3ObjectAclStore(Path.of(storageRoot).resolve("metadata/s3-object-acls"));
    }

    @Bean
    public S3ObjectMetadataHandler s3ObjectMetadataHandler(ReactiveObjectService objectService,
                                                           S3ObjectAclStore objectAclStore) {
        return new S3ObjectMetadataHandler(objectService, objectAclStore);
    }

    @Bean
    public S3BucketConfigHandler s3BucketConfigHandler(ReactiveBucketService bucketService) {
        return new S3BucketConfigHandler(bucketService);
    }

    @Bean
    public S3MultipartPartStore s3MultipartPartStore(
            @Value("${storage.engine.filesystem.root:${java.io.tmpdir}/magrathea-objectstorage/storage-engine}") String storageRoot) {
        return new S3MultipartPartStore(Path.of(storageRoot).resolve("metadata/s3-multipart-parts"));
    }

    @Bean
    public S3MultipartHandler s3MultipartHandler(ReactiveMultipartUploadService multipartUploadService,
                                                 ReactiveObjectService objectService,
                                                 S3MultipartPartStore multipartPartStore,
                                                 S3CapacityProperties capacityProperties) {
        return new S3MultipartHandler(multipartUploadService, objectService, multipartPartStore, capacityProperties);
    }

    @Bean
    public S3SessionHandler s3SessionHandler(ReactiveBucketService bucketService) {
        return new S3SessionHandler(bucketService);
    }

    @Bean
    public S3PathRouter s3PathRouter(S3BucketOperationsHandler bucketOperations,
                                        S3BucketMetadataHandler bucketMetadata,
                                        S3ObjectOperationsHandler objectOperations,
                                        S3ObjectMetadataHandler objectMetadata,
                                        S3BucketConfigHandler bucketConfig,
                                        S3MultipartHandler multipartHandler,
                                        S3SessionHandler sessionHandler) {
        return new S3PathRouter(bucketOperations, bucketMetadata, objectOperations, objectMetadata, bucketConfig, multipartHandler, sessionHandler);
    }

    @Bean
    public RouterFunction<ServerResponse> s3Routes(S3PathRouter router) {
        return router.s3Routes();
    }
}
