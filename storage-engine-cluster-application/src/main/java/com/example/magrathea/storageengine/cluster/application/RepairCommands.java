package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.Objects;

/** Transport-neutral command data proposed to the deterministic control state machine. */
public final class RepairCommands {
    private RepairCommands() { }

    public sealed interface Command permits Ensure, Claim, Renew, Retry, Block, Succeed, Obsolete, Reevaluate {
        String commandId();
        RepairJobId jobId();
        Instant occurredAt();
    }

    public record Ensure(
            String commandId,
            RepairSpecification specification,
            Instant occurredAt,
            NodeIdentity sourceHint) implements Command {
        public Ensure {
            requireCommand(commandId, occurredAt);
            Objects.requireNonNull(specification, "specification");
        }
        @Override public RepairJobId jobId() { return specification.jobId(); }
    }

    public record Claim(
            String commandId, RepairJobId jobId, NodeIdentity owner, String processSession,
            Instant occurredAt, Instant deadline, NodeIdentity sourceHint) implements Command {
        public Claim {
            requireCommand(commandId, occurredAt); Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(owner, "owner"); requireText(processSession, "process session");
            Objects.requireNonNull(deadline, "deadline");
        }
    }

    public record Renew(
            String commandId, RepairJobId jobId, long claimGeneration, NodeIdentity owner,
            String processSession, Instant occurredAt, Instant deadline) implements Command {
        public Renew {
            requireFenced(commandId, jobId, claimGeneration, owner, processSession, occurredAt);
            Objects.requireNonNull(deadline, "deadline");
        }
    }

    public record Retry(
            String commandId, RepairJobId jobId, long claimGeneration, NodeIdentity owner,
            String processSession, Instant occurredAt, String reason) implements Command {
        public Retry { requireFenced(commandId, jobId, claimGeneration, owner, processSession, occurredAt); requireText(reason, "reason"); }
    }

    public record Block(
            String commandId, RepairJobId jobId, long claimGeneration, NodeIdentity owner,
            String processSession, Instant occurredAt, String reason) implements Command {
        public Block { requireFenced(commandId, jobId, claimGeneration, owner, processSession, occurredAt); requireText(reason, "reason"); }
    }

    public record Succeed(
            String commandId, RepairJobId jobId, long claimGeneration, NodeIdentity owner,
            String processSession, Instant occurredAt, long durableLength, String durableSha256,
            String reason) implements Command {
        public Succeed {
            requireFenced(commandId, jobId, claimGeneration, owner, processSession, occurredAt);
            if (durableLength < 0) throw new IllegalArgumentException("durable length must not be negative");
            requireText(durableSha256, "durable SHA-256"); requireText(reason, "reason");
        }
    }

    public record Obsolete(
            String commandId, RepairJobId jobId, long claimGeneration, NodeIdentity owner,
            String processSession, Instant occurredAt, String reason) implements Command {
        public Obsolete {
            requireCommand(commandId, occurredAt); Objects.requireNonNull(jobId, "jobId"); requireText(reason, "reason");
            if (claimGeneration < 0) throw new IllegalArgumentException("claim generation must not be negative");
            if (claimGeneration > 0) { Objects.requireNonNull(owner, "owner"); requireText(processSession, "process session"); }
        }
    }

    public record Reevaluate(
            String commandId, RepairJobId jobId, Instant occurredAt, String reason,
            NodeIdentity sourceHint) implements Command {
        public Reevaluate {
            requireCommand(commandId, occurredAt); Objects.requireNonNull(jobId, "jobId"); requireText(reason, "reason");
        }
    }

    private static void requireFenced(String commandId, RepairJobId jobId, long claimGeneration,
                                      NodeIdentity owner, String session, Instant occurredAt) {
        requireCommand(commandId, occurredAt); Objects.requireNonNull(jobId, "jobId");
        if (claimGeneration < 1) throw new IllegalArgumentException("claim generation must be positive");
        Objects.requireNonNull(owner, "owner"); requireText(session, "process session");
    }

    private static void requireCommand(String commandId, Instant occurredAt) {
        requireText(commandId, "command ID"); Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
