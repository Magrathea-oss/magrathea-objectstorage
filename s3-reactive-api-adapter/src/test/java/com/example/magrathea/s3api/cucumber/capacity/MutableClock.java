package com.example.magrathea.s3api.cucumber.capacity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

final class MutableClock extends Clock {
    private Instant instant = Instant.EPOCH;
    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
    void advanceMillis(long millis) { instant = instant.plusMillis(millis); }
}
