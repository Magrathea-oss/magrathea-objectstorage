package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * SelectRequest — a value object representing an SQL-like SELECT request on object content.
 * <p>
 * Models the request parameters for {@code SelectObjectContent} without framework types.
 * Content bytes are handled by infrastructure — domain only defines the query structure.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record SelectRequest(
    String expression,
    String expressionType,
    InputSerialization input,
    OutputSerialization output
) {

    public SelectRequest {
        Objects.requireNonNull(expression);
        Objects.requireNonNull(expressionType);
        Objects.requireNonNull(input);
        Objects.requireNonNull(output);
        if (expression.isBlank()) throw new IllegalArgumentException("expression must not be blank");
        if (!"SQL".equals(expressionType)) {
            throw new IllegalArgumentException("expressionType must be SQL");
        }
    }

    /**
     * Factory method.
     */
    public static SelectRequest of(String expression, InputSerialization input, OutputSerialization output) {
        return new SelectRequest(expression, "SQL", input, output);
    }

    /**
     * InputSerialization — describes the input format for SELECT.
     */
    public record InputSerialization(
        String format,
        Compression compression
    ) {

        public InputSerialization {
            Objects.requireNonNull(format);
            if (format.isBlank()) throw new IllegalArgumentException("format must not be blank");
        }

        /**
         * Factory method.
         */
        public static InputSerialization of(String format, Compression compression) {
            return new InputSerialization(format, compression);
        }

        public enum Compression { NONE, GZIP, BZIP2 }
    }

    /**
     * OutputSerialization — describes the output format for SELECT.
     */
    public record OutputSerialization(
        String format
    ) {

        public OutputSerialization {
            Objects.requireNonNull(format);
            if (format.isBlank()) throw new IllegalArgumentException("format must not be blank");
        }

        /**
         * Factory method.
         */
        public static OutputSerialization of(String format) {
            return new OutputSerialization(format);
        }
    }
}
