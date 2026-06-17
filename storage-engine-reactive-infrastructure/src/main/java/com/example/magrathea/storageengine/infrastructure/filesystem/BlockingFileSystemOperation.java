package com.example.magrathea.storageengine.infrastructure.filesystem;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * Central helper for wrapping blocking filesystem calls so they do not run on
 * reactive event-loop threads.
 */
final class BlockingFileSystemOperation {

    private BlockingFileSystemOperation() {
    }

    static <T> Mono<T> fromCallable(Callable<T> callable) {
        return Mono.fromCallable(callable)
                .subscribeOn(Schedulers.boundedElastic());
    }

    static Mono<Void> fromRunnable(CheckedRunnable runnable) {
        return Mono.fromRunnable(() -> {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        throw propagate(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private static RuntimeException propagate(Exception e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(e);
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Exception;
    }
}
