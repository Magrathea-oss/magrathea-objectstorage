package spec.com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.valueobject.EcShardLayout;

public class EcShardLayoutSpec extends EcShardLayoutSpecSupport {
    public void it_models_only_a_valid_bounded_ec_shard_identity() {
        shouldBeARecord();

        EcShardLayout data = new EcShardLayout(0, 3, 4, 2, false, 4L * 1024 * 1024);
        match(data.stripeIndex()).shouldReturn(0L);
        match(data.shardIndex()).shouldReturn(3);
        match(data.dataBlocks()).shouldReturn(4);
        match(data.parityBlocks()).shouldReturn(2);
        match(data.parity()).shouldReturn(false);
        match(data.stripeLogicalLength()).shouldReturn(4L * 1024 * 1024);

        EcShardLayout parity = new EcShardLayout(1, 5, 4, 2, true, 2_097_289);
        match(parity.parity()).shouldReturn(true);
        match(parity.stripeLogicalLength()).shouldReturn(2_097_289L);

        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(-1, 0, 4, 2, false, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 0, 0, 2, false, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 0, 4, 0, false, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 0, 32, 1, false, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, -1, 4, 2, false, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 6, 4, 2, true, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 3, 4, 2, true, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 4, 4, 2, false, 1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 0, 4, 2, false, -1));
        shouldThrow(IllegalArgumentException.class).during(() ->
                new EcShardLayout(0, 0, 4, 2, false, 4L * 1024 * 1024 + 1));
    }
}
