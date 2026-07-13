package com.example.magrathea.bootstrap.ep10;

import io.cucumber.java.Before;
import org.junit.jupiter.api.Assumptions;

/** JUnit assumption gate for the external-process EP-10 scenarios. */
public final class Ep10ClusterTestActivation {

    private static final String ENABLED_PROPERTY = "ep10.cluster.tests.enabled";

    @Before(order = Integer.MIN_VALUE)
    public void requireOptInClusterProfile() {
        Assumptions.assumeTrue(
                Boolean.getBoolean(ENABLED_PROPERTY),
                "EP-10 real-process scenarios require -Pep10-cluster-tests");
    }
}
