package com.example.magrathea.s3api.phase2awscli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared per-scenario state for AWS CLI step definition classes.
 * Registered as a singleton Spring bean and explicitly reset in the {@code @Before} hook
 * of {@link Phase2StorageEngineAwsCliSteps} before each scenario.
 * Direct field access works because this bean is not CGLIB-proxied.
 */
public class AwsCliSharedContext {
    public String bucket;
    public String objectKey;
    public String fixtureFile;
    public byte[] fixtureBytes;
    public Path storageRoot;
    public Map<String, String> requestHeaders = new LinkedHashMap<>();
    public int lastGetExitCode = -1;
    public byte[] lastGetBytes = new byte[0];
    public String lastGetStdout = "";
    public String lastGetStderr = "";

    public void reset() {
        bucket = null;
        objectKey = null;
        fixtureFile = null;
        fixtureBytes = null;
        storageRoot = null;
        requestHeaders = new LinkedHashMap<>();
        lastGetExitCode = -1;
        lastGetBytes = new byte[0];
        lastGetStdout = "";
        lastGetStderr = "";
    }
}
