package com.example.magrathea.s3api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.http.codec.AbstractJacksonEncoder;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * Registers Jackson 3 XML encoder and decoder for WebFlux.
 * Spring Boot 4 uses Jackson 3 natively; jackson-dataformat-xml 3.x provides
 * XmlMapper. This config adds application/xml request/response support to WebFlux codecs.
 */
@Configuration
public class JacksonXmlCodecConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        var builder = XmlMapper.builder();
        configurer.customCodecs().register(new JacksonXmlEncoder(builder));
        configurer.customCodecs().register(new JacksonXmlDecoder(builder));
    }

    /**
     * Jackson 3 XML encoder — extends AbstractJacksonEncoder<XmlMapper>.
     * Handles application/xml and text/xml media types.
     */
    public static final class JacksonXmlEncoder
            extends AbstractJacksonEncoder<XmlMapper> {

        private static final MediaType[] DEFAULT_XML_MEDIA_TYPES = {
            MediaType.APPLICATION_XML,
            MediaType.TEXT_XML,
            new MediaType("application", "*+xml")
        };

        public JacksonXmlEncoder(XmlMapper.Builder builder) {
            super(builder, DEFAULT_XML_MEDIA_TYPES);
        }

        public JacksonXmlEncoder(XmlMapper mapper) {
            super(mapper, DEFAULT_XML_MEDIA_TYPES);
        }
    }

    /**
     * Jackson 3 XML decoder — extends AbstractJacksonDecoder<XmlMapper>.
     * Deserializes application/xml request bodies into annotated records.
     */
    public static final class JacksonXmlDecoder
            extends AbstractJacksonDecoder<XmlMapper> {

        private static final MediaType[] DEFAULT_XML_MEDIA_TYPES = {
            MediaType.APPLICATION_XML,
            MediaType.TEXT_XML,
            new MediaType("application", "*+xml")
        };

        public JacksonXmlDecoder(XmlMapper.Builder builder) {
            super(builder, DEFAULT_XML_MEDIA_TYPES);
        }

        public JacksonXmlDecoder(XmlMapper mapper) {
            super(mapper, DEFAULT_XML_MEDIA_TYPES);
        }
    }
}
