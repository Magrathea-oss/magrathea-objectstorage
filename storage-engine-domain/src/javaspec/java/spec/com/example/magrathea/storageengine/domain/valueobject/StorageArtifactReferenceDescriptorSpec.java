package spec.com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.EcShardLayout;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StorageArtifactReferenceDescriptorSpec extends StorageArtifactReferenceDescriptorSpecSupport {
    public void it_optionally_binds_only_kind_consistent_ec_shard_layouts() {
        EcShardLayout dataLayout = new EcShardLayout(0, 3, 4, 2, false, 4L * 1024 * 1024);
        EcShardLayout parityLayout = new EcShardLayout(0, 4, 4, 2, true, 4L * 1024 * 1024);

        StorageArtifactReferenceDescriptor data = descriptor(
                StorageArtifactKind.EC_DATA_SHARD, Optional.of(dataLayout));
        StorageArtifactReferenceDescriptor parity = descriptor(
                StorageArtifactKind.EC_PARITY_SHARD, Optional.of(parityLayout));
        match(data.ecShardLayout()).shouldReturn(Optional.of(dataLayout));
        match(parity.ecShardLayout()).shouldReturn(Optional.of(parityLayout));

        shouldThrow(IllegalArgumentException.class).during(() ->
                descriptor(StorageArtifactKind.EC_DATA_SHARD, Optional.of(parityLayout)));
        shouldThrow(IllegalArgumentException.class).during(() ->
                descriptor(StorageArtifactKind.EC_PARITY_SHARD, Optional.of(dataLayout)));
        shouldThrow(IllegalArgumentException.class).during(() ->
                descriptor(StorageArtifactKind.EC_STRIPE, Optional.of(dataLayout)));
        shouldThrow(IllegalArgumentException.class).during(() ->
                descriptor(StorageArtifactKind.WHOLE_OBJECT, Optional.of(dataLayout)));

        StorageArtifactReferenceDescriptor schema2 = new StorageArtifactReferenceDescriptor(
                StorageArtifactKind.EC_DATA_SHARD,
                chunkId(), fingerprint(), 1, 1, List.of(), checksum(), List.of());
        StorageArtifactReferenceDescriptor schema1 = new StorageArtifactReferenceDescriptor(
                chunkId(), fingerprint(), 1, 1, List.of(), checksum(), List.of());
        match(schema2.ecShardLayout()).shouldReturn(Optional.empty());
        match(schema1.artifactKind()).shouldReturn(StorageArtifactKind.LEGACY_CHUNK);
        match(schema1.ecShardLayout()).shouldReturn(Optional.empty());
    }

    private static StorageArtifactReferenceDescriptor descriptor(
            StorageArtifactKind kind, Optional<EcShardLayout> layout) {
        return new StorageArtifactReferenceDescriptor(
                kind, chunkId(), fingerprint(), 1, 1, List.of(), checksum(), List.of(), layout);
    }

    private static ChunkId chunkId() {
        return new ChunkId(UUID.fromString("00000000-0000-0000-0000-000000000017"));
    }

    private static Fingerprint fingerprint() {
        return new Fingerprint(FingerprintAlgorithm.SHA256, "artifact-fingerprint");
    }

    private static ContentHash checksum() {
        return new ContentHash(ChecksumAlgorithm.SHA256, "artifact-checksum");
    }
}
