package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 Region — value object.
 * Java 17+ record — immutable, transparent, zero boilerplate.
 */
public record Region(String id, String name, boolean supported) {

    public static final Region EU_WEST_1 = new Region("eu-west-1", "EU (Ireland)", true);
    public static final Region EU_CENTRAL_1 = new Region("eu-central-1", "EU (Frankfurt)", true);
    public static final Region US_EAST_1 = new Region("us-east-1", "US East (N. Virginia)", true);
    public static final Region US_WEST_2 = new Region("us-west-2", "US West (Oregon)", true);

    public Region {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
    }
}
