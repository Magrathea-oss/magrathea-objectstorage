package com.example.magrathea.admin.application.port;

import reactor.core.publisher.Mono;

import java.util.Map;

/** Optional provider for one real operational report type. */
public interface AdminOperationalReportProvider {

    String reportType();

    Mono<Map<String, Object>> report();
}
