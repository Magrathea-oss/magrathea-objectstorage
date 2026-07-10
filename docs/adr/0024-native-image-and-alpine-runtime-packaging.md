# ADR 0024: Native image and Alpine runtime packaging

Date: 2026-07-10

Status: Accepted for first packaging slice

## Context

Magrathea needs a distribution path that can run without requiring a Java runtime on the target host. KA-5 tracks a single-binary/self-contained artifact, but the existing container path is a JVM image based on Eclipse Temurin. The project also has a Docker-driven documentation and UI regeneration policy: container builds must regenerate static assets from source-controlled docs and frontend sources rather than copying host-generated assets.

## Decision

Add a GraalVM native-image packaging path for `bootstrap-application`:

- Maven profile `native` runs Spring Boot AOT processing and configures `org.graalvm.buildtools:native-maven-plugin` for the `MagratheaApplication` entry point.
- Maven profile `native-musl` adds musl/static native-image arguments for container builds.
- `Dockerfile.native` builds the native executable in a GraalVM 25 native-image builder and runs it from an Alpine runtime image without a JRE/JDK.
- Keep the existing JVM `Dockerfile` as the canonical/general-purpose image while native packaging is evaluated and hardened.
- Preserve the Docker-driven docs/UI source-of-truth: `Dockerfile.native` runs the Gherkin appendix freshness check and regenerates documentation/frontend assets inside the builder stage, just like the JVM image.

## Consequences

- Operators get a JVM-free packaging path after native compilation and smoke validation with a Spring Boot 4 compatible GraalVM 25 toolchain.
- The first Docker/Alpine musl image slice is validated by image build, Admin API health smoke testing, S3 ListBuckets XML/JSON plus bucket/object PUT/GET smoke testing, absence of Spring Boot's generated-password banner, absence of native reflection/shared-arena runtime errors in the smoke log, and runtime checks that no `java`/`javac` command exists in the final image; the native image path must not be claimed production-complete until runtime behavior for all selected production profile(s) is covered.
- Host builds depend on a compatible GraalVM native-image version; old local native-image installations can fail even after Spring AOT succeeds, and Spring Boot 4 rejects Java 21 native images at startup. Netty on GraalVM 25 also requires shared Arena support for the native runtime path used by this application.
- Alpine runtime requires musl/static native-image output; a normal glibc native executable is not sufficient for `alpine`.
- The native Dockerfile intentionally duplicates the JVM Dockerfile's docs/frontend regeneration steps to preserve deterministic static asset boundaries.
