package com.example.magrathea.s3api.cucumber.pipelineec;

import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort;
import com.example.magrathea.storageengine.infrastructure.pipeline.BoundedEcReconstructionAdapter;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Focused production-adapter context for REQ-PIPELINE-017. */
@CucumberContextConfiguration
@ContextConfiguration(classes = ReqPipeline017EcReconstructionConfig.ReconstructionConfiguration.class)
public class ReqPipeline017EcReconstructionConfig {

    @Configuration
    static class ReconstructionConfiguration {
        @Bean(destroyMethod = "dispose")
        Scheduler reqPipeline017ReconstructionScheduler() {
            return Schedulers.newBoundedElastic(
                    1, 16, "req-pipeline-017-reconstruction");
        }

        @Bean
        BoundedEcReconstructionPort boundedEcReconstructionPort(
                Scheduler reqPipeline017ReconstructionScheduler) {
            return new BoundedEcReconstructionAdapter(
                    reqPipeline017ReconstructionScheduler);
        }
    }
}
