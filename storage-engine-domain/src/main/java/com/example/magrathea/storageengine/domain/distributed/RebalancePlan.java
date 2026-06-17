package com.example.magrathea.storageengine.domain.distributed;

import java.util.List;
import java.util.Objects;

/** Observable rebalance plan. It does not imply that data has already moved. */
public record RebalancePlan(String decision, List<RebalanceMove> moves, boolean observableOnly) {

    public static final String REBALANCE_PLAN_CREATED = "rebalance-plan-created";

    public RebalancePlan {
        requireNonBlank(decision, "decision");
        Objects.requireNonNull(moves, "moves must not be null");
        moves = List.copyOf(moves);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
