package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import com.example.magrathea.storageengine.application.pipeline.ReadPipelineObserver;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class Phase3StorageEventRecorder implements StorageEventListener, ReadPipelineObserver {

    private final List<StorageEvent> events = new CopyOnWriteArrayList<>();
    private final List<ReadObservation> readObservations = new CopyOnWriteArrayList<>();
    private final List<PublicationSnapshot> snapshots = new CopyOnWriteArrayList<>();
    private volatile Path storageRoot;

    void reset() {
        storageRoot = null;
        events.clear();
        snapshots.clear();
        readObservations.clear();
    }

    void observe(Path root) {
        storageRoot = root;
    }

    @Override
    public Mono<Void> onEvent(StorageEvent event) {
        Path root = storageRoot;
        if (root == null) {
            return Mono.empty();
        }
        events.add(event);
        snapshots.add(new PublicationSnapshot(
            event.type(), event.stageName(),
            countFiles(root.resolve("nodes"), path -> !path.getFileName().toString().contains(".tmp.")
                && !path.getFileName().toString().endsWith(".sha256")),
            countFiles(root.resolve("metadata/manifests"), path -> path.getFileName().toString().endsWith(".properties")
                && !path.getFileName().toString().contains(".tmp.")),
            countFiles(root.resolve("metadata/objects"), path -> path.getFileName().toString().endsWith(".json")),
            countFiles(root.resolve("metadata/content-address-index"), Files::isRegularFile)));
        return Mono.empty();
    }

    @Override
    public void chunkReadRequested(String correlationId, int ordinal, ChunkId chunkId) {
        readObservations.add(new ReadObservation("requested", correlationId, ordinal, chunkId.value().toString(), 0));
    }

    @Override
    public void chunkVerified(String correlationId, int ordinal, ChunkId chunkId) {
        readObservations.add(new ReadObservation("verified", correlationId, ordinal, chunkId.value().toString(), 0));
    }

    @Override
    public void responseChunkEmitted(String correlationId, int ordinal, ChunkId chunkId) {
        readObservations.add(new ReadObservation("emitted", correlationId, ordinal, chunkId.value().toString(), 0));
    }

    @Override
    public void downstreamRequested(String correlationId, long requested) {
        readObservations.add(new ReadObservation("demand", correlationId, -1, "", requested));
    }

    List<StorageEvent> events() {
        return List.copyOf(events);
    }

    List<ReadObservation> readObservations() {
        return List.copyOf(readObservations);
    }

    PublicationSnapshot snapshot(StorageEventType type, String stage) {
        return snapshots.stream()
            .filter(snapshot -> snapshot.type() == type && snapshot.stage().equals(stage))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing publication snapshot for " + type + " " + stage));
    }

    private static long countFiles(Path root, Predicate<Path> predicate) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(predicate).count();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    record PublicationSnapshot(StorageEventType type, String stage, long chunkFiles,
                               long manifestFiles, long objectFiles, long contentAddressFiles) {
    }

    record ReadObservation(String kind, String correlationId, int ordinal, String chunkId, long demand) { }
}
