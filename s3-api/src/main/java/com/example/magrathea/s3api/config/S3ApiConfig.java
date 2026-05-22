package com.example.magrathea.s3api.config;

import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.MultipartUploadService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.adapter.web.S3BucketConfigHandler;
import com.example.magrathea.s3api.adapter.web.S3BucketMetadataHandler;
import com.example.magrathea.s3api.adapter.web.S3BucketOperationsHandler;
import com.example.magrathea.s3api.adapter.web.S3MultipartHandler;
import com.example.magrathea.s3api.adapter.web.S3ObjectMetadataHandler;
import com.example.magrathea.s3api.adapter.web.S3ObjectOperationsHandler;
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
    public S3BucketOperationsHandler s3BucketOperationsHandler(BucketService bucketService,
                                                               ObjectService objectService) {
        return new S3BucketOperationsHandler(bucketService, objectService);
    }

    @Bean
    public S3BucketMetadataHandler s3BucketMetadataHandler(BucketService bucketService) {
        return new S3BucketMetadataHandler(bucketService);
    }

    @Bean
    public S3ObjectOperationsHandler s3ObjectOperationsHandler(BucketService bucketService,
                                                               ObjectService objectService) {
        return new S3ObjectOperationsHandler(bucketService, objectService);
    }

    @Bean
    public S3ObjectMetadataHandler s3ObjectMetadataHandler(BucketService bucketService,
                                                           ObjectService objectService) {
        return new S3ObjectMetadataHandler(bucketService, objectService);
    }

    @Bean
    public S3BucketConfigHandler s3BucketConfigHandler(BucketService bucketService) {
        return new S3BucketConfigHandler(bucketService);
    }

    @Bean
    public S3MultipartHandler s3MultipartHandler(MultipartUploadService multipartUploadService) {
        return new S3MultipartHandler(multipartUploadService);
    }

    @Bean
    public S3ProxyRouter s3ProxyRouter(S3BucketOperationsHandler bucketOperations,
                                        S3BucketMetadataHandler bucketMetadata,
                                        S3ObjectOperationsHandler objectOperations,
                                        S3ObjectMetadataHandler objectMetadata,
                                        S3BucketConfigHandler bucketConfig,
                                        S3MultipartHandler multipartHandler) {
        return new S3ProxyRouter(bucketOperations, bucketMetadata, objectOperations, objectMetadata, bucketConfig, multipartHandler);
    }

    @Bean
    public RouterFunction<ServerResponse> s3Routes(S3ProxyRouter router) {
        return router.s3Routes();
    }
}
