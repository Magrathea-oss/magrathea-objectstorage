package com.example.magrathea.s3api.cucumber;

import com.example.magrathea.s3api.config.JacksonXmlCodecConfig;
import com.example.magrathea.s3api.cucumber.steps.CommonSteps;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import tools.jackson.dataformat.xml.XmlMapper;

@SpringBootApplication
@ComponentScan({
    "com.example.magrathea.objectstorage"
})
public class ObjectStorageTestApp {

    @Bean
    public CommonSteps commonSteps() {
        return new CommonSteps();
    }

    @Bean
    public WebTestClient webTestClient(@Qualifier("s3Routes") RouterFunction<ServerResponse> s3Routes) {
        var strategies = HandlerStrategies.builder()
            .codecs(config -> config.customCodecs().register(
                new JacksonXmlCodecConfig.JacksonXmlEncoder(XmlMapper.builder())))
            .build();
        return WebTestClient.bindToRouterFunction(s3Routes)
            .handlerStrategies(strategies)
            .build();
    }
}
