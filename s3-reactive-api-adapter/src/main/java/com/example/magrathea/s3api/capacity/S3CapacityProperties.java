package com.example.magrathea.s3api.capacity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configurable resource bounds for the S3 server only. */
@ConfigurationProperties("s3.capacity")
public class S3CapacityProperties {

    private boolean enabled = true;
    private long maxSinglePutBytes = 256L * 1024 * 1024;
    private long maxMultipartPartBytes = 64L * 1024 * 1024;
    private long maxAssembledMultipartBytes = 256L * 1024 * 1024;
    private int maxConcurrentRequests = 16;
    private int maxTcpConnections = 64;
    private Duration requestTimeout = Duration.ofSeconds(300);
    private double rateLimitPerSecond = 100.0;
    private int rateLimitBurst = 200;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getMaxSinglePutBytes() { return maxSinglePutBytes; }
    public void setMaxSinglePutBytes(long value) { this.maxSinglePutBytes = positive(value, "max-single-put-bytes"); }
    public long getMaxMultipartPartBytes() { return maxMultipartPartBytes; }
    public void setMaxMultipartPartBytes(long value) { this.maxMultipartPartBytes = positive(value, "max-multipart-part-bytes"); }
    public long getMaxAssembledMultipartBytes() { return maxAssembledMultipartBytes; }
    public void setMaxAssembledMultipartBytes(long value) { this.maxAssembledMultipartBytes = positive(value, "max-assembled-multipart-bytes"); }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int value) { this.maxConcurrentRequests = positive(value, "max-concurrent-requests"); }
    public int getMaxTcpConnections() { return maxTcpConnections; }
    public void setMaxTcpConnections(int value) { this.maxTcpConnections = positive(value, "max-tcp-connections"); }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException("request-timeout must be positive");
        this.requestTimeout = value;
    }
    public double getRateLimitPerSecond() { return rateLimitPerSecond; }
    public void setRateLimitPerSecond(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("rate-limit-per-second must be positive");
        this.rateLimitPerSecond = value;
    }
    public int getRateLimitBurst() { return rateLimitBurst; }
    public void setRateLimitBurst(int value) { this.rateLimitBurst = positive(value, "rate-limit-burst"); }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
