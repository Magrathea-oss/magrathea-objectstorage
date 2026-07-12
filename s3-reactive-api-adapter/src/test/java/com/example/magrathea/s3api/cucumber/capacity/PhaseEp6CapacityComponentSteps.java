package com.example.magrathea.s3api.cucumber.capacity;

import com.example.magrathea.s3api.capacity.S3CapacityMetrics;
import com.example.magrathea.s3api.capacity.S3CapacityProperties;
import com.example.magrathea.s3api.capacity.S3CapacityWebFilter;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp6CapacityComponentSteps {
    private S3CapacityProperties properties;
    private SimpleMeterRegistry registry;
    private S3CapacityWebFilter filter;
    private MutableClock clock;
    private MockServerWebExchange exchange;
    private AtomicBoolean invoked;
    private Disposable holder;
    private long observedChunks;

    @Given("a default S3 capacity configuration")
    public void defaultConfiguration() { properties = new S3CapacityProperties(); }

    @Then("capacity controls are enabled with {long} single PUT bytes, {long} multipart part bytes, and {long} assembled bytes")
    public void defaultSizes(long single, long part, long assembled) {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxSinglePutBytes()).isEqualTo(single);
        assertThat(properties.getMaxMultipartPartBytes()).isEqualTo(part);
        assertThat(properties.getMaxAssembledMultipartBytes()).isEqualTo(assembled);
    }

    @And("the defaults allow {int} concurrent requests with a {int} second timeout")
    public void defaultAdmission(int concurrent, int seconds) {
        assertThat(properties.getMaxConcurrentRequests()).isEqualTo(concurrent);
        assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(seconds));
    }

    @And("the default token bucket refills {int} requests per second with burst {int}")
    public void defaultRate(int rate, int burst) {
        assertThat(properties.getRateLimitPerSecond()).isEqualTo(rate);
        assertThat(properties.getRateLimitBurst()).isEqualTo(burst);
    }

    @Given("a component capacity filter with a {long} byte single PUT limit")
    public void componentFilter(long limit) {
        properties = new S3CapacityProperties();
        properties.setMaxSinglePutBytes(limit);
        properties.setRateLimitBurst(100);
        registry = new SimpleMeterRegistry();
        filter = new S3CapacityWebFilter(properties, new S3CapacityMetrics(registry));
    }

    @When("PUT declares a {long} byte Content-Length")
    public void declaredPut(long length) {
        exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/bucket/known.bin")
            .header("Content-Length", Long.toString(length)).build());
        invoked = new AtomicBoolean();
        filter.filter(exchange, downstream(invoked)).block(Duration.ofSeconds(1));
    }

    @Then("the filter rejects it as {string} without invoking downstream storage")
    public void rejectedBeforeStorage(String code) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(code);
        assertThat(invoked).isFalse();
    }

    @When("an unknown-length PUT streams chunks of {int} and {int} bytes")
    public void streamedPut(int first, int second) {
        var factory = new DefaultDataBufferFactory();
        var body = Flux.just(factory.wrap(new byte[first]), factory.wrap(new byte[second]));
        exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/bucket/stream.bin").body(body));
        observedChunks = 0;
        WebFilterChain consuming = current -> current.getRequest().getBody()
            .doOnNext(buffer -> observedChunks++)
            .then();
        filter.filter(exchange, consuming).block(Duration.ofSeconds(1));
    }

    @Then("the filter rejects it incrementally as {string} after observing the excess chunk")
    public void rejectedIncrementally(String code) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(code);
        assertThat(observedChunks).isEqualTo(1);
    }

    @Given("deterministic capacity controls with one concurrency permit, a {int} millisecond timeout, and a two-token burst")
    public void deterministicControls(int timeoutMillis) {
        properties = new S3CapacityProperties();
        properties.setMaxConcurrentRequests(1);
        properties.setRequestTimeout(Duration.ofMillis(timeoutMillis));
        properties.setRateLimitPerSecond(100);
        properties.setRateLimitBurst(2);
        registry = new SimpleMeterRegistry();
        clock = new MutableClock();
        filter = new S3CapacityWebFilter(properties, new S3CapacityMetrics(registry), clock);
    }

    @Then("a third immediate token request is rejected as {string} with Retry-After {string}")
    public void burstRejected(String code, String retryAfter) {
        completeRequest("/bucket/one");
        completeRequest("/bucket/two");
        exchange = execute("/bucket/three", current -> Mono.empty());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo(retryAfter);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(code);
    }

    @And("advancing one refill interval admits another token request")
    public void refillAdmits() {
        clock.advanceMillis(10);
        exchange = execute("/bucket/four", current -> Mono.empty());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        resetFilterForConcurrency();
    }

    @When("one request holds the only concurrency permit")
    public void holdPermit() {
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/bucket/holder").build());
        holder = filter.filter(exchange, current -> Mono.never()).subscribe();
    }

    @Then("another request is rejected immediately as {string} without a pending subscription")
    public void concurrencyRejected(String code) {
        var subscribed = new AtomicBoolean();
        exchange = execute("/bucket/excess", current -> Mono.defer(() -> {
            subscribed.set(true);
            return Mono.empty();
        }));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(code);
        assertThat(subscribed).isFalse();
    }

    @And("cancelling the holder permits the next request")
    public void cancellingReleases() {
        holder.dispose();
        completeRequest("/bucket/recovered");
    }

    @When("an admitted request remains stalled beyond {int} milliseconds")
    public void requestStalls(int millis) {
        exchange = execute("/bucket/stalled", current -> Mono.never());
        assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofMillis(millis));
    }

    @Then("it is cancelled as {string} and its permit is reusable")
    public void timeoutAndReuse(String code) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains(code);
        completeRequest("/bucket/after-timeout");
    }

    @And("capacity metric tags contain only operation and outcome vocabularies")
    public void boundedTags() {
        assertThat(registry.getMeters()).isNotEmpty();
        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags().stream()
            .map(tag -> tag.getKey()).collect(java.util.stream.Collectors.toSet()))
            .isSubsetOf(Set.of("operation", "outcome")));
    }

    private void resetFilterForConcurrency() {
        properties.setRateLimitBurst(100);
        filter = new S3CapacityWebFilter(properties, new S3CapacityMetrics(registry), clock);
    }

    private void completeRequest(String path) { execute(path, current -> Mono.empty()); }

    private MockServerWebExchange execute(String path, WebFilterChain chain) {
        var result = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        filter.filter(result, chain).block(Duration.ofSeconds(1));
        return result;
    }

    private static WebFilterChain downstream(AtomicBoolean invoked) {
        return current -> Mono.fromRunnable(() -> invoked.set(true));
    }
}
