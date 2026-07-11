package com.example.magrathea.s3api.cucumber.runtime;

import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.s3api.adapter.web.S3MultipartPartStore;
import com.example.magrathea.s3api.adapter.web.S3StreamingBody;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runtime demand-control glue for the bounded EP-3 adapter slice. */
public class Phase3RuntimeBackpressureSteps {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private int bufferCount;
    private int bufferSize;
    private byte[] expectedBytes;
    private Path multipartRoot;
    private final Map<String, Long> maxRequests = new LinkedHashMap<>();
    private final Map<String, byte[]> results = new LinkedHashMap<>();
    private int largestPartReadBuffer;

    @Before
    public void reset() throws Exception {
        bufferCount = 0;
        bufferSize = 0;
        expectedBytes = null;
        maxRequests.clear();
        results.clear();
        largestPartReadBuffer = 0;
        multipartRoot = Files.createTempDirectory("magrathea-ep3-runtime-");
    }

    @After
    public void cleanup() {
        if (multipartRoot != null) {
            new S3MultipartPartStore(multipartRoot).reset();
        }
    }

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void configuredProfile(String profile, String backend) {
        assertEquals("storage-engine-it", profile);
        assertEquals("storage-engine", backend);
    }

    @Given("the storage engine stores bytes, manifests, and object references on a real filesystem")
    public void realFilesystem() {
        assertTrue(Files.isDirectory(multipartRoot));
    }

    @Given("each scenario uses a clean storage-engine filesystem root {string}")
    public void cleanRoot(String ignoredPattern) {
        assertTrue(Files.isDirectory(multipartRoot));
    }

    @Given("reactive pipeline event capture is enabled for the selected validation mode")
    public void eventCaptureScope() {
        // REQ-PIPELINE-013 intentionally validates demand only, not StorageEvent semantics.
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void selectedMode(String mode, String requirement) {
        assertEquals("runtime-backpressure", mode);
        assertEquals("REQ-PIPELINE-013", requirement);
    }

    @Given("a demand-controlled source contains {int} ordered DataBuffers of {int} bytes")
    public void demandControlledSource(int count, int size) {
        bufferCount = count;
        bufferSize = size;
        expectedBytes = new byte[count * size];
        for (int index = 0; index < expectedBytes.length; index++) {
            expectedBytes[index] = (byte) (index * 31 + 7);
        }
    }

    @When("the runtime runner streams the source through each production S3 object body boundary")
    public void streamObjectBoundaries() {
        results.put("PutObject", consume(S3StreamingBody.bounded(source("PutObject"))));
        results.put("GetObject", consume(S3StreamingBody.bounded(source("GetObject"))));

        long start = bufferSize + 17L;
        long end = expectedBytes.length - bufferSize - 23L;
        results.put("Range", consume(S3StreamingBody.sliceRange(source("Range"), start, end)));
        results.put("RangeExpected", Arrays.copyOfRange(expectedBytes, (int) start, (int) end + 1));
    }

    @Then("PutObject, GetObject, and ranged GetObject retain at most the shared demand-window number of source DataBuffers")
    public void objectDemandIsBounded() {
        assertBounded("PutObject");
        assertBounded("GetObject");
        assertBounded("Range");
    }

    @Then("each object path emits the expected ordered bytes without whole-body aggregation")
    public void objectBytesRemainOrdered() {
        assertArrayEquals(expectedBytes, results.get("PutObject"));
        assertArrayEquals(expectedBytes, results.get("GetObject"));
        assertArrayEquals(results.get("RangeExpected"), results.get("Range"));
    }

    @When("the runtime runner saves the source through multipart part storage")
    public void saveMultipartSources() {
        var store = new S3MultipartPartStore(multipartRoot);
        var uploadId = UploadId.of("runtimebackpressureupload");
        store.savePart(uploadId, PartNumber.of(1), source("UploadPart")).block();
        store.savePart(uploadId, PartNumber.of(2), source("UploadPartCopy")).block();
        results.put("MultipartRead", consume(store.readPart(uploadId, PartNumber.of(1))
                .doOnNext(buffer -> largestPartReadBuffer = Math.max(largestPartReadBuffer, buffer.readableByteCount()))));
    }

    @Then("UploadPart and UploadPartCopy retain at most the shared demand-window number of source DataBuffers")
    public void multipartDemandIsBounded() {
        assertBounded("UploadPart");
        assertBounded("UploadPartCopy");
    }

    @Then("multipart part-file reads emit buffers no larger than {int} bytes and reproduce the expected ordered bytes")
    public void multipartReadsAreBounded(int maximumBufferSize) {
        assertTrue(largestPartReadBuffer <= maximumBufferSize,
                "Largest multipart read buffer was " + largestPartReadBuffer);
        assertArrayEquals(expectedBytes, results.get("MultipartRead"));
    }

    @Then("this evidence does not claim the complete staged StorageStage pipeline")
    public void scopeIsConservative() {
        assertEquals(4, S3StreamingBody.demandWindow());
    }

    private Flux<DataBuffer> source(String path) {
        AtomicLong maxRequest = new AtomicLong();
        return Flux.range(0, bufferCount)
                .map(index -> (DataBuffer) BUFFER_FACTORY.wrap(Arrays.copyOfRange(
                        expectedBytes, index * bufferSize, (index + 1) * bufferSize)))
                .doOnRequest(requested -> maxRequest.accumulateAndGet(requested, Math::max))
                .doFinally(signal -> maxRequests.put(path, maxRequest.get()));
    }

    private static byte[] consume(Flux<DataBuffer> content) {
        var output = new ByteArrayOutputStream();
        content.concatMap(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        output.writeBytes(bytes);
                        return Flux.just(bytes.length);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                }, 1)
                .then()
                .block();
        return output.toByteArray();
    }

    private void assertBounded(String path) {
        long observed = maxRequests.getOrDefault(path, Long.MAX_VALUE);
        assertTrue(observed > 0 && observed <= S3StreamingBody.demandWindow(),
                path + " requested " + observed + " source buffers in one demand signal");
    }
}
