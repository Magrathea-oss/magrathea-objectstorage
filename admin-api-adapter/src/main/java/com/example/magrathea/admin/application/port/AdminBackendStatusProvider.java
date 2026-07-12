package com.example.magrathea.admin.application.port;

import reactor.core.publisher.Mono;

import java.util.Map;

/** Supplies truthful runtime backend and configuration evidence to the Admin API. */
@FunctionalInterface
public interface AdminBackendStatusProvider {

    Mono<Map<String, Object>> backendStatus();
}
