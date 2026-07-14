package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.ReferencePageQuery;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Base64;

/** Focused fail-closed codec evidence shared by the REQ-CLUSTER-027 Story BDD gate. */
public final class ReferencePageCodecEvidence {
    private ReferencePageCodecEvidence() { }

    public static Result verify() {
        ReferencePageQuery.Cursor cursor = new ReferencePageQuery.Cursor(
                "ep10-anti-entropy-archive", "evidence/2026/cursor.bin", 7);
        ReferencePageQuery maximum = new ReferencePageQuery(
                cursor, ReferencePageQuery.MAXIMUM_LIMIT);
        ControlPlaneCodec.QueryReferences decoded =
                (ControlPlaneCodec.QueryReferences) ControlPlaneCodec.readCommand(
                        ControlPlaneCodec.queryReferences(maximum));

        boolean invalidLimits = rejected(() -> new ReferencePageQuery(null, 0))
                && rejected(() -> new ReferencePageQuery(
                        null, ReferencePageQuery.MAXIMUM_LIMIT + 1));
        boolean invalidCursors = rejected(() -> new ReferencePageQuery.Cursor("", "key", 1))
                && rejected(() -> new ReferencePageQuery.Cursor("bucket", "", 1))
                && rejected(() -> new ReferencePageQuery.Cursor("bucket", "key", 0));

        byte[] validQuery = ControlPlaneCodec.queryReferences(new ReferencePageQuery(null, 2));
        byte[] trailingQuery = Arrays.copyOf(validQuery, validQuery.length + 1);
        trailingQuery[trailingQuery.length - 1] = 99;
        boolean malformedQueries = rejected(() -> ControlPlaneCodec.readCommand(new byte[0]))
                && rejected(() -> ControlPlaneCodec.readCommand(new byte[]{15, 1}))
                && rejected(() -> ControlPlaneCodec.readCommand(trailingQuery))
                && rejected(() -> ControlPlaneCodec.queryReferences(null));

        boolean malformedPages = rejected(() -> ControlPlaneCodec.decodeReferencePage("***"))
                && rejected(() -> ControlPlaneCodec.decodeReferencePage(encoded(out -> {
                    out.writeInt(0);
                    out.writeBoolean(true);
                    out.writeBoolean(false);
                    out.writeByte(99);
                })))
                && rejected(() -> ControlPlaneCodec.decodeReferencePage(encoded(out -> {
                    out.writeInt(0);
                    out.writeBoolean(true);
                    out.writeBoolean(true);
                    out.writeUTF("bucket");
                    out.writeUTF("key");
                    out.writeLong(1);
                })));
        boolean hardLimit = rejected(() -> ControlPlaneCodec.decodeReferencePage(encoded(out ->
                out.writeInt(ReferencePageQuery.MAXIMUM_LIMIT + 1))));

        return new Result(maximum.equals(decoded.query()), invalidLimits, invalidCursors,
                malformedQueries, malformedPages, hardLimit);
    }

    private static boolean rejected(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static String encoded(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writer.write(out);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public record Result(boolean maximumLimitRoundTrip, boolean invalidLimits,
                         boolean invalidCursors, boolean malformedQueries,
                         boolean malformedPages, boolean hardLimit) {
        public boolean complete() {
            return maximumLimitRoundTrip && invalidLimits && invalidCursors
                    && malformedQueries && malformedPages && hardLimit;
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
