package com.example.magrathea.bootstrap;

import com.example.magrathea.cluster.data.grpc.ReplicaTransferFaultPlan;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterWriteCoordinator;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ReferencePublicationBarrier;
import com.example.magrathea.storageengine.cluster.application.ReplicaReadPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.time.Duration;

/** Production composition selected only by the combined storage-engine,cluster profiles. */
@Configuration(proxyBeanMethods = false)
@Profile("storage-engine & cluster")
@EnableConfigurationProperties(ClusterProfileProperties.class)
public class ClusterProfileConfiguration {
    @Bean(destroyMethod = "close")
    ClusterNodeRuntime clusterNodeRuntime(
            ClusterProfileProperties properties,
            ObjectProvider<ReplicaTransferFaultPlan> transferFaultPlan,
            ObjectProvider<ReferencePublicationBarrier> publicationBarrier) throws IOException {
        return new ClusterNodeRuntime(
                properties,
                transferFaultPlan.getIfAvailable(ReplicaTransferFaultPlan::none),
                publicationBarrier.getIfAvailable(ReferencePublicationBarrier::none));
    }

    @Bean
    NodeIdentity clusterLocalNodeIdentity(ClusterNodeRuntime runtime) {
        return runtime.localIdentity();
    }

    @Bean
    LocalArtifactPort clusterLocalArtifactPort(ClusterNodeRuntime runtime) {
        return runtime.artifacts();
    }

    @Bean
    ClusterControlPlanePort clusterControlPlanePort(ClusterNodeRuntime runtime) {
        return runtime.controlPlane();
    }

    @Bean
    ReplicaReadPort clusterReplicaReadPort(ClusterNodeRuntime runtime) {
        return runtime.reads();
    }

    @Bean
    ClusterWriteCoordinator clusterWriteCoordinator(ClusterNodeRuntime runtime) {
        return runtime.coordinator();
    }

    @Bean
    Duration clusterReadDeadline(ClusterProfileProperties properties) {
        return properties.getDeadlines().getRead();
    }
}
