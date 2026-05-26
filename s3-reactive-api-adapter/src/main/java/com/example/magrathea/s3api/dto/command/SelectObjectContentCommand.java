package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * Command DTO for POST /{bucket}/{key}?select (SelectObjectContent).
 * S3 SelectObjectContent XML body specifying the SQL expression and serialization format.
 */
@JacksonXmlRootElement(localName = "SelectObjectContentRequest")
public record SelectObjectContentCommand(
    @JacksonXmlProperty(localName = "Expression")
    String expression,
    @JacksonXmlProperty(localName = "ExpressionType")
    String expressionType,
    @JacksonXmlProperty(localName = "RequestProgress")
    RequestProgress requestProgress,
    @JacksonXmlProperty(localName = "InputSerialization")
    InputSerialization inputSerialization,
    @JacksonXmlProperty(localName = "OutputSerialization")
    OutputSerialization outputSerialization
) {
    public record RequestProgress(
        @JacksonXmlProperty(localName = "Enabled")
        boolean enabled
    ) {}

    public record InputSerialization(
        @JacksonXmlProperty(localName = "CSV")
        CsvInput csv,
        @JacksonXmlProperty(localName = "JSON")
        JsonInput json,
        @JacksonXmlProperty(localName = "Parquet")
        ParquetInput parquet
    ) {
        public record CsvInput(
            @JacksonXmlProperty(localName = "FileHeaderInfo")
            String fileHeaderInfo,
            @JacksonXmlProperty(localName = "RecordDelimiter")
            String recordDelimiter,
            @JacksonXmlProperty(localName = "FieldDelimiter")
            String fieldDelimiter,
            @JacksonXmlProperty(localName = "QuoteCharacter")
            String quoteCharacter,
            @JacksonXmlProperty(localName = "Comments")
            String comments
        ) {}

        public record JsonInput(
            @JacksonXmlProperty(localName = "Type")
            String type
        ) {}

        public record ParquetInput() {}
    }

    public record OutputSerialization(
        @JacksonXmlProperty(localName = "CSV")
        CsvOutput csv,
        @JacksonXmlProperty(localName = "JSON")
        JsonOutput json
    ) {
        public record CsvOutput(
            @JacksonXmlProperty(localName = "FileHeaderInfo")
            String fileHeaderInfo,
            @JacksonXmlProperty(localName = "RecordDelimiter")
            String recordDelimiter,
            @JacksonXmlProperty(localName = "FieldDelimiter")
            String fieldDelimiter,
            @JacksonXmlProperty(localName = "QuoteCharacter")
            String quoteCharacter
        ) {}

        public record JsonOutput(
            @JacksonXmlProperty(localName = "Type")
            String type
        ) {}
    }
}
