package com.example.magrathea.s3api.security;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class S3AuthenticatedRequestDecorationWebFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(authentication -> authentication instanceof S3AuthenticationToken)
            .map(authentication -> securedExchange(exchange, authentication.getName()))
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    private static ServerWebExchange securedExchange(ServerWebExchange exchange, String principal) {
        byte[] cachedBody = exchange.getAttribute(S3SigV4ServerAuthenticationConverter.CACHED_BODY_ATTRIBUTE);
        var requestBuilder = exchange.getRequest().mutate()
            .header("x-magrathea-principal", principal);
        if (cachedBody == null) {
            return exchange.mutate().request(requestBuilder.build()).build();
        }
        var originalRequest = requestBuilder.build();
        var decoratedRequest = new ServerHttpRequestDecorator(originalRequest) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(cachedBody);
                return Flux.just(buffer);
            }
        };
        return exchange.mutate().request(decoratedRequest).build();
    }
}
