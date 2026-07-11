package com.example.magrathea.s3api.cucumber.specs;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static-architecture step definitions for Phase 3 reactive pipeline Ability specs.
 *
 * <p>These steps make the streaming/no-whole-object-aggregation contract executable as
 * Cucumber documentation while the deeper StorageStage/StorageContext pipeline-unit
 * runner remains future work.</p>
 */
public class Phase3ReactivePipelineStaticArchitectureSteps {

    private Path inspectedPath;
    private String inspectedSource;
    private String inspectedMethodBody;

    @Before
    public void resetStaticArchitectureState() {
        inspectedPath = null;
        inspectedSource = null;
        inspectedMethodBody = null;
    }

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void s3ApiConfiguredWithProfileAndBackend(String profile, String backend) {
        // Static-architecture validation inspects source code only; runtime profile/backend
        // behavior is validated by the WebTestClient and process runners.
    }

    @Given("the storage engine stores bytes, manifests, and object references on a real filesystem")
    public void storageEngineStoresBytesManifestsAndReferencesOnFilesystem() {
        // No-op for static source inspection.
    }

    @Given("each scenario uses a clean storage-engine filesystem root {string}")
    public void eachScenarioUsesCleanStorageEngineFilesystemRoot(String storageRoot) {
        // No-op for static source inspection.
    }

    @Given("reactive pipeline event capture is enabled for the selected validation mode")
    public void reactivePipelineEventCaptureIsEnabled() {
        // No-op for static source inspection.
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationModeIsSelectedForRequirement(String validationMode, String requirementId) {
        assertTrue("static-architecture".equals(validationMode),
                "This runner validates only static-architecture examples, not " + validationMode);
        assertTrue(requirementId.startsWith("REQ-PIPELINE-"),
                "Expected a Phase 3 pipeline requirement id but got " + requirementId);
    }

    @Given("production source path {string} implements the S3 PutObject route")
    public void productionSourcePathImplementsS3PutObjectRoute(String relativePath) throws IOException {
        inspectSourcePath(relativePath);
        assertTrue(inspectedSource.contains("public Mono<ServerResponse> putObject(ServerRequest request)"),
                "Expected S3 PutObject handler method in " + inspectedPath);
    }

    @Given("production source path {string} implements the S3 GetObject route")
    public void productionSourcePathImplementsS3GetObjectRoute(String relativePath) throws IOException {
        inspectSourcePath(relativePath);
        assertTrue(inspectedSource.contains("public Mono<ServerResponse> getObject(ServerRequest request)"),
                "Expected S3 GetObject handler method in " + inspectedPath);
    }

    @Given("production source path {string} implements fixed-window deduplication")
    public void productionSourcePathImplementsFixedWindowDeduplication(String relativePath) throws IOException {
        inspectSourcePath(relativePath);
        assertTrue(inspectedSource.contains("class FixedWindowDedupStep"),
                "Expected FixedWindowDedupStep class in " + inspectedPath);
    }

    @Given("production source path {string} implements the S3 multipart handler")
    public void productionSourcePathImplementsS3MultipartHandler(String relativePath) throws IOException {
        inspectSourcePath(relativePath);
        assertTrue(inspectedSource.contains("class S3MultipartHandler"),
                "Expected S3MultipartHandler class in " + inspectedPath);
    }

    @Given("production source path {string} implements multipart part body storage")
    public void productionSourcePathImplementsMultipartPartBodyStorage(String relativePath) throws IOException {
        inspectSourcePath(relativePath);
        assertTrue(inspectedSource.contains("class S3MultipartPartStore"),
                "Expected S3MultipartPartStore class in " + inspectedPath);
    }

    @Given("bucket {string} exists for key {string}")
    public void bucketExistsForKey(String bucket, String key) {
        // Static source inspection does not create buckets.
    }

    @Given("the configured dedup window size is {string}")
    public void configuredDedupWindowSizeIs(String windowSize) {
        assertTrue(windowSize.endsWith("MiB"), "Expected human-readable MiB window size: " + windowSize);
    }

    @When("the static architecture runner inspects method {string} in the production source path")
    public void staticArchitectureRunnerInspectsMethod(String methodName) {
        assertSourceLoaded();
        var serverResponseSignature = "public Mono<ServerResponse> " + methodName + "(ServerRequest request)";
        inspectedMethodBody = inspectedSource.contains(serverResponseSignature)
                ? methodBody(inspectedSource, serverResponseSignature)
                : methodBodyByName(inspectedSource, methodName);
    }

    @When("the static architecture runner inspects the production source path")
    public void staticArchitectureRunnerInspectsTheProductionSourcePath() {
        assertSourceLoaded();
        inspectedMethodBody = null;
    }

    @Then("method {string} does not invoke {string}")
    public void methodDoesNotInvoke(String methodName, String forbiddenCall) {
        assertMethodLoaded();
        assertFalse(inspectedMethodBody.contains(forbiddenCall + "("),
                methodName + " must not invoke " + forbiddenCall + " in " + inspectedPath);
    }

    @Then("method {string} does not materialize the request body into a whole-object {string} array")
    public void methodDoesNotMaterializeRequestBodyIntoWholeObjectArray(String methodName, String forbiddenArrayName) {
        assertMethodLoaded();
        assertFalse(inspectedMethodBody.contains(forbiddenArrayName),
                methodName + " must not materialize the full request body into " + forbiddenArrayName);
        assertFalse(inspectedMethodBody.contains("new byte[joined.readableByteCount()]"),
                methodName + " must not allocate a full-object byte array from a joined DataBuffer");
    }

    @Then("method {string} does not pass storage a Flux created by re-wrapping a whole-object byte array")
    public void methodDoesNotPassStorageFluxRewrappingWholeObjectArray(String methodName) {
        assertMethodLoaded();
        assertFalse(inspectedMethodBody.contains("Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))"),
                methodName + " must not re-wrap a whole-object byte array as a Flux for storage");
        assertFalse(inspectedMethodBody.contains("Flux.just(new DefaultDataBufferFactory().wrap(bytes))"),
                methodName + " must not re-wrap a whole-object byte array as a Flux for storage");
    }

    @Then("method {string} passes {string} directly to multipart part storage")
    public void methodPassesSourceDirectlyToMultipartPartStorage(String methodName, String sourceExpression) {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains("partStore.savePart")
                && inspectedMethodBody.contains(sourceExpression),
                methodName + " must pass " + sourceExpression + " directly to multipart part storage");
    }

    @Then("method {string} streams copied source content directly to multipart part storage")
    public void methodStreamsCopiedSourceContentDirectlyToMultipartPartStorage(String methodName) {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains("partStore.savePart(uploadId, targetPartNumber, objectService.getContent(sourceObjectKey))"),
                methodName + " must stream source object content directly into multipart part storage");
    }

    @Then("method {string} accepts multipart content as {string}")
    public void methodAcceptsMultipartContentAs(String methodName, String parameter) {
        assertSourceLoaded();
        assertTrue(inspectedSource.contains(methodName + "(UploadId uploadId, PartNumber partNumber, " + parameter + ")"),
                methodName + " must accept multipart content as " + parameter);
    }

    @Then("method {string} writes multipart content with {string}")
    public void methodWritesMultipartContentWith(String methodName, String writer) {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains(writer + "(measuredContent"),
                methodName + " must write the measured DataBuffer stream with " + writer);
    }

    @Then("method {string} computes part measurements incrementally for each DataBuffer")
    public void methodComputesPartMeasurementsIncrementally(String methodName) {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains("S3StreamingBody.bounded(content).doOnNext(buffer ->"),
                methodName + " must bound demand and measure each incoming DataBuffer incrementally");
        assertTrue(inspectedMethodBody.contains("size.addAndGet(buffer.readableByteCount())"),
                methodName + " must compute part size incrementally");
        assertTrue(inspectedMethodBody.contains("digest.update(buffer.toByteBuffer().asReadOnlyBuffer())"),
                methodName + " must compute part MD5 incrementally without consuming the stream twice");
    }

    @Then("method {string} reads multipart content with {string}")
    public void methodReadsMultipartContentWith(String methodName, String reader) {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains(reader + "(partPath(uploadId, partNumber)"),
                methodName + " must read multipart part files with " + reader);
    }

    @Then("the implementation computes the single-part ETag and supported checksum headers while teeing the DataBuffer stream into saveObjectWithContent")
    public void implementationComputesEtagWhileTeeingDataBufferStreamIntoStorage() {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains("request.bodyToFlux(DataBuffer.class)"),
                "PutObject must consume the request as a DataBuffer Flux");
        assertTrue(inspectedMethodBody.contains(".doOnNext(uploadDigest::update)"),
                "PutObject must tee each DataBuffer into UploadDigest without joining the body");
        assertTrue(inspectedMethodBody.contains("saveObjectWithContent(active, content, effectiveStorageClass)"),
                "PutObject must pass the same streaming content Flux to storage");
        assertTrue(inspectedSource.contains("persistComputedUploadMeasurements"),
                "PutObject must persist computed upload measurements after streaming storage completes");
    }

    @Then("the S3 PutObject response can expose the computed ETag without requiring a second full-body aggregation")
    public void putObjectResponseExposesComputedEtagWithoutSecondAggregation() {
        assertMethodLoaded();
        assertTrue(inspectedMethodBody.contains("persistComputedUploadMeasurements"),
                "PutObject must use persisted computed upload measurements for response metadata");
        assertFalse(inspectedMethodBody.contains("collectList()"),
                "PutObject must not collect the full request body before responding");
        assertFalse(inspectedMethodBody.contains("DataBufferUtils.join("),
                "PutObject must not join the full request body before responding");
    }

    @Then("method {string} does not collect object content before a non-range response")
    public void methodDoesNotCollectObjectContentBeforeNonRangeResponse(String methodName) {
        assertMethodLoaded();
        assertFalse(inspectedMethodBody.contains("return oc.content().collectList()\n                        .flatMap(buffers -> S3ResponseBuilder.okWithBody"),
                methodName + " must not collect object content before building the non-range response");
    }

    @Then("method {string} passes {string} through the shared finite-demand boundary to {string}")
    public void methodPassesSourceThroughBoundedTarget(String methodName, String sourceExpression, String targetExpression) {
        assertMethodLoaded();
        var expectedCall = targetExpression + "(obj, S3StreamingBody.bounded(" + sourceExpression + "))";
        assertTrue(inspectedMethodBody.contains(expectedCall),
                methodName + " must pass " + sourceExpression + " through S3StreamingBody.bounded to "
                + targetExpression + " but expected call was not found: " + expectedCall);
    }

    @Then("range handling streams through the explicit Range header branch")
    public void rangeHandlingStreamsThroughExplicitRangeHeaderBranch() {
        assertMethodLoaded();
        int rangeBranch = inspectedMethodBody.indexOf("if (rangeHeader != null && !rangeHeader.isBlank())");
        assertTrue(rangeBranch >= 0, "Expected explicit Range header branch in getObject");
        int rangeServe = inspectedMethodBody.indexOf("serveRange(obj, oc.content(), rangeHeader)", rangeBranch);
        assertTrue(rangeServe > rangeBranch,
                "Range handling must stream oc.content() through serveRange inside the explicit Range branch");
    }

    @Then("method {string} passes {string} directly to range helper {string}")
    public void methodPassesSourceDirectlyToRangeHelper(String methodName, String sourceExpression, String helperName) {
        assertMethodLoaded();
        var expectedCall = helperName + "(obj, " + sourceExpression + ", rangeHeader)";
        assertTrue(inspectedMethodBody.contains(expectedCall),
                methodName + " must pass " + sourceExpression + " directly to " + helperName
                + " but expected call was not found: " + expectedCall);
    }

    @Then("helper {string} accepts streaming content as {string} instead of a collected buffer list")
    public void helperAcceptsStreamingContentInsteadOfCollectedList(String helperName, String streamingParameter) {
        assertSourceLoaded();
        assertTrue(inspectedSource.contains(helperName + "(S3Object obj,\n            " + streamingParameter),
                helperName + " must accept streaming content parameter " + streamingParameter);
        assertFalse(inspectedSource.contains(helperName + "(S3Object obj,\n            java.util.List<DataBuffer> buffers"),
                helperName + " must not accept a pre-collected DataBuffer list");
    }

    @Then("helper {string} attaches {string} to {string}")
    public void helperAttachesExpressionToTarget(String helperName, String expression, String target) {
        assertSourceLoaded();
        var compactSource = inspectedSource.replaceAll("\\s+", "");
        assertTrue(compactSource.contains((target + "(" + expression + ")").replaceAll("\\s+", "")),
                helperName + " must attach " + expression + " to " + target);
    }

    @Then("shared helper {string} emits finite-demand per-buffer range slices without building a full-object array")
    public void sharedHelperEmitsFiniteDemandRangeSlices(String helperName) throws IOException {
        assertTrue("S3StreamingBody.sliceRange".equals(helperName), "Unexpected shared range helper: " + helperName);
        inspectSourcePath("s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3StreamingBody.java");
        assertTrue(inspectedSource.contains("public static Flux<DataBuffer> sliceRange"),
                "Expected shared sliceRange helper");
        assertTrue(inspectedSource.contains("return bounded(content).handle"),
                "sliceRange must apply the finite-demand boundary before slicing");
        assertTrue(inspectedSource.contains("byte[] bufferBytes = new byte[readable]"),
                "sliceRange must bound temporary arrays to the current DataBuffer size");
        assertTrue(inspectedSource.contains("Arrays.copyOfRange(bufferBytes, sliceStart, sliceEndExclusive)"),
                "sliceRange must emit only the overlapping slice from each DataBuffer");
        assertFalse(inspectedSource.contains("byte[] fullBody"),
                "sliceRange must not build a full-object byte array");
    }

    @Then("FixedWindowDedupStep does not invoke {string}")
    public void fixedWindowDedupStepDoesNotInvoke(String forbiddenCall) {
        assertSourceLoaded();
        assertFalse(inspectedSource.contains(forbiddenCall + "("),
                "FixedWindowDedupStep must not invoke " + forbiddenCall);
    }

    @Then("FixedWindowDedupStep does not materialize the complete FileUnit into a whole-object {string} array")
    public void fixedWindowDedupStepDoesNotMaterializeCompleteFileUnit(String forbiddenArrayName) {
        assertSourceLoaded();
        assertFalse(inspectedSource.contains(forbiddenArrayName),
                "FixedWindowDedupStep must not materialize the complete FileUnit into " + forbiddenArrayName);
    }

    @Then("FixedWindowDedupStep fingerprints and looks up each configured window as DataBuffers are consumed incrementally")
    public void fixedWindowDedupStepFingerprintsAndLooksUpEachWindowIncrementally() {
        assertSourceLoaded();
        assertTrue(inspectedSource.contains("fileUnit.data()"),
                "FixedWindowDedupStep must consume FileUnit DataBuffers");
        assertTrue(inspectedSource.contains("concatMap(buffer -> Flux.fromIterable(accumulator.append(buffer))"),
                "FixedWindowDedupStep must append each incoming DataBuffer to the window accumulator");
        assertTrue(inspectedSource.contains("deduplicateWindow("),
                "FixedWindowDedupStep must deduplicate each completed window");
        assertTrue(inspectedSource.contains("contentAddressIndex.find(deviceHash, fingerprint)"),
                "FixedWindowDedupStep must look up each window fingerprint in the content-address index");
    }

    @Then("emitted ChunkUnit data is bounded by the configured dedup window size rather than total FileUnit size")
    public void emittedChunkUnitDataIsBoundedByConfiguredWindowSize() {
        assertSourceLoaded();
        assertTrue(inspectedSource.contains("private final byte[] currentWindow = new byte[chunkSize];"),
                "FixedWindowDedupStep must bound retained window bytes by chunkSize");
        assertTrue(inspectedSource.contains("Arrays.copyOf(currentWindow, currentLength)"),
                "FixedWindowDedupStep must emit only the current completed/partial window");
        assertFalse(inspectedSource.contains("splitIntoWindows(allBytes"),
                "FixedWindowDedupStep must not split a previously materialized whole FileUnit");
    }

    private void inspectSourcePath(String relativePath) throws IOException {
        inspectedPath = sourcePath(relativePath);
        inspectedSource = Files.readString(inspectedPath);
        inspectedMethodBody = null;
    }

    private void assertSourceLoaded() {
        assertTrue(inspectedSource != null && inspectedPath != null,
                "A production source path must be loaded before inspection");
    }

    private void assertMethodLoaded() {
        assertTrue(inspectedMethodBody != null,
                "A production method body must be loaded before method-level inspection");
    }

    private static Path sourcePath(String relativePath) {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find " + relativePath + " from working directory "
                + Path.of("").toAbsolutePath());
    }

    private static String methodBodyByName(String source, String methodName) {
        int nameStart = source.indexOf(methodName + "(");
        assertTrue(nameStart >= 0, "Expected method name not found: " + methodName);
        int signatureStart = source.lastIndexOf('\n', nameStart);
        return methodBodyFrom(source, signatureStart >= 0 ? signatureStart : nameStart, methodName);
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "Expected method signature not found: " + signature);
        return methodBodyFrom(source, signatureStart, signature);
    }

    private static String methodBodyFrom(String source, int signatureStart, String description) {
        int braceStart = source.indexOf('{', signatureStart);
        assertTrue(braceStart >= 0, "Expected method opening brace not found: " + description);

        int depth = 0;
        for (int index = braceStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, index + 1);
                }
            }
        }
        throw new IllegalStateException("Could not extract method body for " + description);
    }
}
