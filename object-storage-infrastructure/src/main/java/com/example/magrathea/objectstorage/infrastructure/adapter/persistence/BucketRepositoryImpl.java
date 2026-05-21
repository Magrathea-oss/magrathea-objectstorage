package com.example.magrathea.objectstorage.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.repository.BucketRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of BucketRepository.
 * Repository interface returns CompletableFuture — implementation fulfills async.
 */
@Repository
public class BucketRepositoryImpl implements BucketRepository {

    private final ConcurrentHashMap<String, Bucket> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<Bucket>> findById(Bucket.Id id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id.value())));
    }

    @Override
    public CompletableFuture<Optional<Bucket>> findByName(String name) {
        var found = store.values().stream()
            .filter(b -> b.name().equals(name))
            .findFirst();
        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletableFuture<List<Bucket>> findAll() {
        return CompletableFuture.completedFuture(List.copyOf(store.values()));
    }

    @Override
    public CompletableFuture<Void> save(Bucket bucket) {
        store.put(bucket.id().value(), bucket);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(Bucket.Id id) {
        store.remove(id.value());
        return CompletableFuture.completedFuture(null);
    }

    /** Reset state — used by test cleanup hooks. */
    public void reset() {
        store.clear();
    }
}
