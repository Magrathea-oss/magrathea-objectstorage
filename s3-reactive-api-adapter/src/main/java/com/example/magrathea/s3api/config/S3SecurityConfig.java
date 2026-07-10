package com.example.magrathea.s3api.config;

import com.example.magrathea.s3api.security.FileS3SecurityAuditSink;
import com.example.magrathea.s3api.security.InMemoryS3SecurityAuditSink;
import com.example.magrathea.s3api.security.LocalS3KeyManagementService;
import com.example.magrathea.s3api.security.S3AccessDeniedHandler;
import com.example.magrathea.s3api.security.S3AuthenticatedRequestDecorationWebFilter;
import com.example.magrathea.s3api.security.S3AuthenticationEntryPoint;
import com.example.magrathea.s3api.security.S3CredentialStore;
import com.example.magrathea.s3api.security.S3ReactiveAuthenticationManager;
import com.example.magrathea.s3api.security.S3ReactiveAuthorizationManager;
import com.example.magrathea.s3api.security.S3SecurityAuditSink;
import com.example.magrathea.s3api.security.S3SecurityAuthorizer;
import com.example.magrathea.s3api.security.S3SecurityProperties;
import com.example.magrathea.s3api.security.S3ServerSideEncryptionWebFilter;
import com.example.magrathea.s3api.security.S3SigV4ServerAuthenticationConverter;
import com.example.magrathea.s3api.security.S3SigV4Verifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

@AutoConfiguration
@EnableConfigurationProperties(S3SecurityProperties.class)
public class S3SecurityConfig {

    // ADR 0023: Spring Security Reactive is the EP-1 backbone. Secured mode uses
    // durable local backing services for credentials, policies, audit, and key material;
    // in-memory audit remains a test fallback when no audit file is configured.

    @Bean
    @ConditionalOnMissingBean(ReactiveUserDetailsService.class)
    public ReactiveUserDetailsService s3NoOpReactiveUserDetailsService() {
        return username -> Mono.empty();
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveUserDetailsPasswordService.class)
    public ReactiveUserDetailsPasswordService s3NoOpReactiveUserDetailsPasswordService() {
        return ReactiveUserDetailsPasswordService.NOOP;
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain s3PermitAllSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain s3SecuredSecurityWebFilterChain(ServerHttpSecurity http,
                                                                  S3SigV4ServerAuthenticationConverter authenticationConverter,
                                                                  S3ReactiveAuthenticationManager authenticationManager,
                                                                  S3ReactiveAuthorizationManager authorizationManager,
                                                                  S3AuthenticationEntryPoint authenticationEntryPoint,
                                                                  S3AccessDeniedHandler accessDeniedHandler) {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(authenticationConverter);
        authenticationWebFilter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(authenticationEntryPoint));

        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .authorizeExchange(exchanges -> exchanges.anyExchange().access(authorizationManager))
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public LocalS3KeyManagementService s3KeyManagementService(S3SecurityProperties properties) {
        String keyFile = properties.getKeyFile() == null || properties.getKeyFile().isBlank()
            ? "data/security/s3-local-master.key"
            : properties.getKeyFile();
        return new LocalS3KeyManagementService(Path.of(keyFile));
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3CredentialStore s3CredentialStore(S3SecurityProperties properties,
                                               LocalS3KeyManagementService keyManagementService) {
        return new S3CredentialStore(properties, keyManagementService);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3SigV4Verifier s3SigV4Verifier(S3SecurityProperties properties,
                                           S3CredentialStore credentialStore) {
        return new S3SigV4Verifier(properties, credentialStore);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3SecurityAuthorizer s3SecurityAuthorizer(S3SecurityProperties properties) {
        return new S3SecurityAuthorizer(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3SecurityAuditSink s3SecurityAuditSink(S3SecurityProperties properties) {
        if (properties.getAuditFile() != null && !properties.getAuditFile().isBlank()) {
            return new FileS3SecurityAuditSink(Path.of(properties.getAuditFile()));
        }
        return new InMemoryS3SecurityAuditSink();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3SigV4ServerAuthenticationConverter s3SigV4ServerAuthenticationConverter(S3SigV4Verifier verifier,
                                                                                     S3SecurityProperties properties,
                                                                                     S3SecurityAuditSink auditSink) {
        return new S3SigV4ServerAuthenticationConverter(verifier, properties, auditSink);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3ReactiveAuthenticationManager s3ReactiveAuthenticationManager() {
        return new S3ReactiveAuthenticationManager();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3ReactiveAuthorizationManager s3ReactiveAuthorizationManager(S3SecurityAuthorizer authorizer,
                                                                         S3SecurityAuditSink auditSink) {
        return new S3ReactiveAuthorizationManager(authorizer, auditSink);
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3AuthenticationEntryPoint s3AuthenticationEntryPoint() {
        return new S3AuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3AccessDeniedHandler s3AccessDeniedHandler() {
        return new S3AccessDeniedHandler();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3AuthenticatedRequestDecorationWebFilter s3AuthenticatedRequestDecorationWebFilter() {
        return new S3AuthenticatedRequestDecorationWebFilter();
    }

    @Bean
    @ConditionalOnProperty(name = "s3.security.enabled", havingValue = "true")
    public S3ServerSideEncryptionWebFilter s3ServerSideEncryptionWebFilter(S3SecurityProperties properties,
                                                                           S3SecurityAuditSink auditSink,
                                                                           LocalS3KeyManagementService keyManagementService) {
        return new S3ServerSideEncryptionWebFilter(properties, auditSink, keyManagementService);
    }
}
