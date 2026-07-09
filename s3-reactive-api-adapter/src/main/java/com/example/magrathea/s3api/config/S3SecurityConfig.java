package com.example.magrathea.s3api.config;

import com.example.magrathea.s3api.security.S3CredentialStore;
import com.example.magrathea.s3api.security.S3SecurityProperties;
import com.example.magrathea.s3api.security.S3SecurityWebFilter;
import com.example.magrathea.s3api.security.S3SigV4Verifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(S3SecurityProperties.class)
public class S3SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3CredentialStore s3CredentialStore(S3SecurityProperties properties) {
        return new S3CredentialStore(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3SigV4Verifier s3SigV4Verifier(S3SecurityProperties properties,
                                           S3CredentialStore credentialStore) {
        return new S3SigV4Verifier(properties, credentialStore);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3SecurityWebFilter s3SecurityWebFilter(S3SecurityProperties properties,
                                                   S3SigV4Verifier verifier) {
        return new S3SecurityWebFilter(properties, verifier);
    }
}
