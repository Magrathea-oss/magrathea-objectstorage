package com.example.magrathea.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** External configuration for one process in the fixed EP-10 A/B/C cluster. */
@ConfigurationProperties("magrathea.cluster")
public class ClusterProfileProperties {
    private String nodeId;
    private String topologyEpoch = "topology-1";
    private String policyEpoch = "policy-1";
    private List<Peer> peers = new ArrayList<>();
    private Roots roots = new Roots();
    private Tls tls = new Tls();
    private Deadlines deadlines = new Deadlines();
    private AntiEntropy antiEntropy = new AntiEntropy();

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getTopologyEpoch() { return topologyEpoch; }
    public void setTopologyEpoch(String topologyEpoch) { this.topologyEpoch = topologyEpoch; }
    public String getPolicyEpoch() { return policyEpoch; }
    public void setPolicyEpoch(String policyEpoch) { this.policyEpoch = policyEpoch; }
    public List<Peer> getPeers() { return peers; }
    public void setPeers(List<Peer> peers) { this.peers = peers; }
    public Roots getRoots() { return roots; }
    public void setRoots(Roots roots) { this.roots = roots; }
    public Tls getTls() { return tls; }
    public void setTls(Tls tls) { this.tls = tls; }
    public Deadlines getDeadlines() { return deadlines; }
    public void setDeadlines(Deadlines deadlines) { this.deadlines = deadlines; }
    public AntiEntropy getAntiEntropy() { return antiEntropy; }
    public void setAntiEntropy(AntiEntropy antiEntropy) { this.antiEntropy = antiEntropy; }

    public static class Peer {
        private String name;
        private String id;
        private String controlAddress;
        private String dataAddress;
        private String failureDomain;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getControlAddress() { return controlAddress; }
        public void setControlAddress(String controlAddress) { this.controlAddress = controlAddress; }
        public String getDataAddress() { return dataAddress; }
        public void setDataAddress(String dataAddress) { this.dataAddress = dataAddress; }
        public String getFailureDomain() { return failureDomain; }
        public void setFailureDomain(String failureDomain) { this.failureDomain = failureDomain; }
    }

    public static class Roots {
        private Path identity;
        private Path ratis;
        private Path objects;
        private Path temporary;
        private Path runtime;

        public Path getIdentity() { return identity; }
        public void setIdentity(Path identity) { this.identity = identity; }
        public Path getRatis() { return ratis; }
        public void setRatis(Path ratis) { this.ratis = ratis; }
        public Path getObjects() { return objects; }
        public void setObjects(Path objects) { this.objects = objects; }
        public Path getTemporary() { return temporary; }
        public void setTemporary(Path temporary) { this.temporary = temporary; }
        public Path getRuntime() { return runtime; }
        public void setRuntime(Path runtime) { this.runtime = runtime; }
    }

    public static class Tls {
        private Material control = new Material();
        private Material data = new Material();

        public Material getControl() { return control; }
        public void setControl(Material control) { this.control = control; }
        public Material getData() { return data; }
        public void setData(Material data) { this.data = data; }
    }

    public static class Material {
        private Path certificate;
        private Path privateKey;
        private Path trustCertificate;

        public Path getCertificate() { return certificate; }
        public void setCertificate(Path certificate) { this.certificate = certificate; }
        public Path getPrivateKey() { return privateKey; }
        public void setPrivateKey(Path privateKey) { this.privateKey = privateKey; }
        public Path getTrustCertificate() { return trustCertificate; }
        public void setTrustCertificate(Path trustCertificate) { this.trustCertificate = trustCertificate; }
    }

    public static class AntiEntropy {
        private Duration interval = Duration.ofSeconds(30);
        private int pageSize = 16;

        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }

    public static class Deadlines {
        private Duration transfer = Duration.ofSeconds(5);
        private Duration read = Duration.ofSeconds(5);
        private Duration slowReplicaAcceptance = Duration.ZERO;

        public Duration getTransfer() { return transfer; }
        public void setTransfer(Duration transfer) { this.transfer = transfer; }
        public Duration getRead() { return read; }
        public void setRead(Duration read) { this.read = read; }
        public Duration getSlowReplicaAcceptance() { return slowReplicaAcceptance; }
        public void setSlowReplicaAcceptance(Duration slowReplicaAcceptance) {
            this.slowReplicaAcceptance = slowReplicaAcceptance;
        }
    }
}
