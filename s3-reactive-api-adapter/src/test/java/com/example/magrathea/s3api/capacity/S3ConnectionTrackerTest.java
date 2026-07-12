package com.example.magrathea.s3api.capacity;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.netty.Connection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3ConnectionTrackerTest {

    @Test
    void exposesBoundedMetricsAndReleasesExactlyOnceOnClose() {
        S3CapacityProperties properties = new S3CapacityProperties();
        properties.setMaxTcpConnections(4);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        S3ConnectionTracker tracker = new S3ConnectionTracker(properties, registry);
        List<Disposable> closeCallbacks = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            Connection connection = mock(Connection.class);
            doAnswer(invocation -> {
                closeCallbacks.add(invocation.getArgument(0));
                return connection;
            }).when(connection).onDispose(any(Disposable.class));
            tracker.connected(connection);
        }

        Connection excess = mock(Connection.class);
        tracker.connected(excess);

        assertThat(registry.get(S3ConnectionTracker.ACTIVE_METRIC).gauge().value()).isEqualTo(4);
        assertThat(registry.get(S3ConnectionTracker.REJECTED_METRIC).counter().count()).isEqualTo(1);
        assertThat(registry.get(S3ConnectionTracker.ACTIVE_METRIC).gauge().getId().getTags()).isEmpty();
        assertThat(registry.get(S3ConnectionTracker.REJECTED_METRIC).counter().getId().getTags()).isEmpty();
        verify(excess).dispose();

        closeCallbacks.getFirst().dispose();
        closeCallbacks.getFirst().dispose();
        assertThat(registry.get(S3ConnectionTracker.ACTIVE_METRIC).gauge().value()).isEqualTo(3);
    }
}
