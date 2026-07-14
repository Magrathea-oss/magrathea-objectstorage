package com.example.magrathea.s3api.cucumber.ep10architecture;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/** Empty context: REQ-CLUSTER-014 validates repository inputs and starts no application runtime. */
@CucumberContextConfiguration
@ContextConfiguration(classes = ReqCluster014ArchitectureContractConfig.EmptyConfiguration.class)
public class ReqCluster014ArchitectureContractConfig {
    static class EmptyConfiguration { }
}
