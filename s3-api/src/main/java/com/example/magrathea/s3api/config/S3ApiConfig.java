package com.example.magrathea.s3api.config;

import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.adapter.web.S3ProxyRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Auto-configuration for S3 API module.
 * Activates when:
 *  - object-storage-application is on the classpath (BucketService, ObjectService available)
 *  - s3.api.enabled property is not false
 *
 * When s3-api module is excluded from the build, this config is not loaded.
 * When s3-api is included but disabled via property, beans are not created.
 */
@AutoConfiguration
@ConditionalOnClass({ BucketService.class, ObjectService.class })
@ConditionalOnProperty(name = "s3.api.enabled", havingValue = "true", matchIfMissing = true)
public class S3ApiConfig {

    @Bean
    public S3ProxyRouter s3ProxyRouter(BucketService bucketService,
                                        ObjectService objectService) {
        return new S3ProxyRouter(bucketService, objectService);
    }

    @Bean
    public RouterFunction<ServerResponse> s3Routes(S3ProxyRouter router) {
        return router.s3Routes();
    }
}
