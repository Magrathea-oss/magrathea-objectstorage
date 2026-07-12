package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.BucketCapacityPort;
import com.example.magrathea.storageengine.application.port.BucketQuotaExceededException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Filesystem-backed, process-atomic bucket quota ledger. */
public final class FileSystemBucketCapacityStore implements BucketCapacityPort {
    private final Path ledger;
    private final Object monitor = new Object();
    private final Map<String, MutableCapacity> capacities = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();

    public FileSystemBucketCapacityStore(Path storageRoot) {
        this.ledger = storageRoot.resolve("metadata").resolve("bucket-capacity.properties");
        if (!load()) {
            reconcileExistingReferences(storageRoot.resolve("metadata").resolve("s3-object-references"));
            if (!capacities.isEmpty()) {
                persist();
            }
        }
        // In-flight reservations are deliberately not durable across process lifetime.
        // Only committed logical usage survives restart; abandoned reservations are released.
    }

    @Override
    public Mono<Reservation> reserve(
            String bucket, String objectKey, long requestedBytes, long replacedBytes) {
        return blocking(() -> {
            if (requestedBytes < 0 || replacedBytes < 0) {
                throw new IllegalArgumentException("Capacity reservation byte counts must be non-negative");
            }
            synchronized (monitor) {
                MutableCapacity state = capacities.computeIfAbsent(bucket, ignored -> new MutableCapacity());
                long credit = Math.min(replacedBytes, state.usedBytes);
                long netReservation = Math.max(0, requestedBytes - credit);
                if (state.quotaBytes >= 0
                        && state.usedBytes + state.reservedBytes + netReservation > state.quotaBytes) {
                    state.rejectedReservations++;
                    state.lastRejectedBytes = requestedBytes;
                    persist();
                    throw new BucketQuotaExceededException(snapshot(bucket, state), requestedBytes);
                }
                state.reservedBytes += netReservation;
                Reservation reservation = new Reservation(
                        UUID.randomUUID().toString(), bucket, objectKey, requestedBytes, credit);
                reservations.put(reservation.id(), reservation);
                persist();
                return reservation;
            }
        });
    }

    @Override
    public Mono<Reservation> resize(Reservation reservation, long requestedBytes) {
        return blocking(() -> {
            if (requestedBytes < 0) {
                throw new IllegalArgumentException("requestedBytes must be non-negative");
            }
            synchronized (monitor) {
                Reservation active = reservations.get(reservation.id());
                if (active == null) {
                    throw new IllegalStateException("Unknown or completed capacity reservation: " + reservation.id());
                }
                MutableCapacity state = capacities.get(active.bucket());
                long oldNet = Math.max(0, active.requestedBytes() - active.replacedBytes());
                long newNet = Math.max(0, requestedBytes - active.replacedBytes());
                long projectedReserved = state.reservedBytes - oldNet + newNet;
                if (state.quotaBytes >= 0 && state.usedBytes + projectedReserved > state.quotaBytes) {
                    state.rejectedReservations++;
                    state.lastRejectedBytes = requestedBytes;
                    persist();
                    throw new BucketQuotaExceededException(snapshot(active.bucket(), state), requestedBytes);
                }
                Reservation resized = new Reservation(active.id(), active.bucket(), active.objectKey(),
                        requestedBytes, active.replacedBytes());
                reservations.put(resized.id(), resized);
                state.reservedBytes = projectedReserved;
                persist();
                return resized;
            }
        });
    }

    @Override
    public Mono<BucketCapacity> configureQuota(String bucket, long quotaBytes) {
        return blocking(() -> {
            if (quotaBytes < 0) {
                throw new IllegalArgumentException("quotaBytes must be non-negative");
            }
            synchronized (monitor) {
                MutableCapacity state = capacities.computeIfAbsent(bucket, ignored -> new MutableCapacity());
                if (state.usedBytes + state.reservedBytes > quotaBytes) {
                    throw new IllegalArgumentException("quotaBytes is below current used plus reserved bytes");
                }
                state.quotaBytes = quotaBytes;
                persist();
                return snapshot(bucket, state);
            }
        });
    }

    @Override
    public Mono<BucketCapacity> capacity(String bucket) {
        return blocking(() -> {
            synchronized (monitor) {
                return snapshot(bucket, capacities.computeIfAbsent(bucket, ignored -> new MutableCapacity()));
            }
        });
    }

    @Override
    public Mono<BucketCapacity> commit(Reservation reservation, long committedBytes) {
        return blocking(() -> {
            synchronized (monitor) {
                Reservation active = reservations.get(reservation.id());
                if (active == null) {
                    throw new IllegalStateException("Unknown or completed capacity reservation: " + reservation.id());
                }
                MutableCapacity state = capacities.get(active.bucket());
                long ownReservation = Math.max(0, active.requestedBytes() - active.replacedBytes());
                long projectedUsed = Math.max(0, state.usedBytes - active.replacedBytes()) + committedBytes;
                long otherReservations = state.reservedBytes - ownReservation;
                if (state.quotaBytes >= 0 && projectedUsed + otherReservations > state.quotaBytes) {
                    state.rejectedReservations++;
                    state.lastRejectedBytes = committedBytes;
                    persist();
                    throw new BucketQuotaExceededException(snapshot(active.bucket(), state), committedBytes);
                }
                reservations.remove(reservation.id());
                state.reservedBytes -= ownReservation;
                state.usedBytes = projectedUsed;
                persist();
                return snapshot(active.bucket(), state);
            }
        });
    }

    @Override
    public Mono<Void> release(Reservation reservation) {
        return blocking(() -> {
            synchronized (monitor) {
                Reservation active = reservations.remove(reservation.id());
                if (active != null) {
                    MutableCapacity state = capacities.get(active.bucket());
                    state.reservedBytes -= Math.max(0, active.requestedBytes() - active.replacedBytes());
                    persist();
                }
                return null;
            }
        }).then();
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    private BucketCapacity snapshot(String bucket, MutableCapacity state) {
        return new BucketCapacity(bucket, state.usedBytes, state.reservedBytes,
                state.quotaBytes, state.rejectedReservations, state.lastRejectedBytes);
    }

    private boolean load() {
        synchronized (monitor) {
            if (!Files.isRegularFile(ledger)) {
                return false;
            }
            Properties properties = new Properties();
            try (var input = Files.newInputStream(ledger)) {
                properties.load(input);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load bucket capacity ledger: " + ledger, e);
            }
            properties.stringPropertyNames().stream()
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .distinct()
                    .forEach(bucket -> capacities.put(bucket, new MutableCapacity(
                            number(properties, bucket + ".usedBytes", 0),
                            0,
                            number(properties, bucket + ".quotaBytes", -1),
                            number(properties, bucket + ".rejectedReservations", 0),
                            number(properties, bucket + ".lastRejectedBytes", 0))));
            return true;
        }
    }

    private void reconcileExistingReferences(Path referencesRoot) {
        if (!Files.isDirectory(referencesRoot)) {
            return;
        }
        synchronized (monitor) {
            try (var references = Files.walk(referencesRoot)) {
                references.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".properties"))
                        .forEach(path -> {
                            Properties properties = new Properties();
                            try (var input = Files.newInputStream(path)) {
                                properties.load(input);
                            } catch (IOException error) {
                                throw new UncheckedIOException(
                                        "Failed to reconcile object reference capacity: " + path, error);
                            }
                            String bucket = properties.getProperty("bucket");
                            long size = Long.parseLong(properties.getProperty("size", "0"));
                            if (bucket != null && !bucket.isBlank() && size >= 0) {
                                capacities.computeIfAbsent(bucket, ignored -> new MutableCapacity()).usedBytes += size;
                            }
                        });
            } catch (IOException error) {
                throw new UncheckedIOException("Failed to reconcile existing bucket capacity", error);
            }
        }
    }

    private void persist() {
        try {
            Files.createDirectories(ledger.getParent());
            Properties properties = new Properties();
            capacities.forEach((bucket, state) -> {
                properties.setProperty(bucket + ".usedBytes", Long.toString(state.usedBytes));
                properties.setProperty(bucket + ".reservedBytes", Long.toString(state.reservedBytes));
                properties.setProperty(bucket + ".quotaBytes", Long.toString(state.quotaBytes));
                properties.setProperty(bucket + ".rejectedReservations", Long.toString(state.rejectedReservations));
                properties.setProperty(bucket + ".lastRejectedBytes", Long.toString(state.lastRejectedBytes));
            });
            Path temporary = ledger.resolveSibling(ledger.getFileName() + ".tmp." + UUID.randomUUID());
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
                properties.store(output, "Magrathea durable bucket logical-byte capacity ledger");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, ledger, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, ledger, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist bucket capacity ledger: " + ledger, e);
        }
    }

    private static long number(Properties properties, String name, long fallback) {
        return Long.parseLong(properties.getProperty(name, Long.toString(fallback)));
    }

    private static final class MutableCapacity {
        long usedBytes;
        long reservedBytes;
        long quotaBytes = -1;
        long rejectedReservations;
        long lastRejectedBytes;

        MutableCapacity() { }
        MutableCapacity(long usedBytes, long reservedBytes, long quotaBytes,
                long rejectedReservations, long lastRejectedBytes) {
            this.usedBytes = usedBytes;
            this.reservedBytes = reservedBytes;
            this.quotaBytes = quotaBytes;
            this.rejectedReservations = rejectedReservations;
            this.lastRejectedBytes = lastRejectedBytes;
        }
    }
}
