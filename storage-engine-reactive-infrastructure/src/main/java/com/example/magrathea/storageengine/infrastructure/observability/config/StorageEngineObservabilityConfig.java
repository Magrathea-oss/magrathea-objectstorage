package com.example.magrathea.storageengine.infrastructure.observability.config;

import com.example.magrathea.storageengine.application.observability.StorageSpanRecorder;
import com.example.magrathea.storageengine.application.pipeline.CompositeStorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.infrastructure.observability.MicrometerStorageEventListener;
import com.example.magrathea.storageengine.infrastructure.observability.OpenTelemetryStorageEventListener;
import com.example.magrathea.storageengine.infrastructure.observability.StorageOperationalLoggingEventListener;
import com.example.magrathea.storageengine.infrastructure.observability.StorageTracingEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class StorageEngineObservabilityConfig {

    @Bean
    public StorageEventPublisher storageEventPublisher(ObjectProvider<StorageEventListener> listeners) {
        List<StorageEventListener> listenerList = listeners.orderedStream().toList();
        if (listenerList.isEmpty()) {
            return StorageEventPublisher.noop();
        }
        return new CompositeStorageEventPublisher(listenerList);
    }

    @Bean
    public StorageEventListener micrometerStorageEventListener(ObjectProvider<MeterRegistry> meterRegistry) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return event -> Mono.empty();
        }
        return new MicrometerStorageEventListener(registry);
    }

    @Bean
    public StorageEventListener storageTracingEventListener(ObjectProvider<StorageSpanRecorder> recorder) {
        return new StorageTracingEventListener(recorder.getIfAvailable(StorageSpanRecorder::noop));
    }

    @Bean
    public StorageEventListener openTelemetryStorageEventListener(ObjectProvider<Tracer> tracer) {
        Tracer availableTracer = tracer.getIfAvailable();
        if (availableTracer == null) {
            return event -> Mono.empty();
        }
        return new OpenTelemetryStorageEventListener(availableTracer);
    }

    @Bean
    public StorageEventListener storageOperationalLoggingEventListener() {
        return new StorageOperationalLoggingEventListener();
    }
}
