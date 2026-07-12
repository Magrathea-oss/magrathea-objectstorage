package com.example.magrathea.s3api.cucumber.ep4;

import com.example.magrathea.storageengine.application.observability.StorageObservabilityFields;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
public class Ep4CapacityTestSupport {
    @Bean
    public CapacityEventRecorder capacityEventRecorder() {
        return new CapacityEventRecorder();
    }

    public static Path newStorageRoot(String mode) {
        try {
            return Files.createTempDirectory("magrathea-ep4-" + mode + "-");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public static Path extractCatalog(String mode, String directory, String file) {
        try {
            Path target = Files.createTempDirectory("magrathea-ep4-" + mode + "-catalog-").resolve(directory);
            Files.createDirectories(target);
            try (InputStream input = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(directory + "/" + file)) {
                if (input == null) {
                    throw new IOException("Missing classpath catalog " + directory + "/" + file);
                }
                Files.write(target.resolve(file), input.readAllBytes());
            }
            return target;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public static final class CapacityEventRecorder implements StorageEventListener {
        private final List<StorageEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public Mono<Void> onEvent(StorageEvent event) {
            events.add(event);
            return Mono.empty();
        }

        public void reset() {
            events.clear();
        }

        public Map<String, String> capacityFailureFields() {
            return events.stream()
                .filter(event -> event.type() == StorageEventType.STAGE_FAILED)
                .map(StorageObservabilityFields::safeFields)
                .filter(fields -> "capacity-exhausted".equals(fields.get("failure.classification")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No capacity-exhausted storage event was emitted"));
        }
    }
}
