@spec @non-functional-requirement @packaging @ka5-distribution
Ability: Native-image distribution packaging
  Maintainers need a self-contained distribution path that can be built by Maven and packaged into a minimal container
  so Magrathea Object Storage can run without requiring a Java runtime in the final deployment image.

  Rule: Native packaging must preserve the documented source-of-truth boundaries

    @REQ-PKG-001 @native-image @spring-aot @implemented-and-validated
    Scenario: Maven prepares the bootstrap application for GraalVM native-image compilation
      Given the runnable application entry point is "com.example.magrathea.bootstrap.MagratheaApplication"
      And the build uses the Maven profiles "native" and "native-musl" for the bootstrap application module
      When maintainers run the validation mode "Maven native AOT packaging"
      Then Spring Boot AOT processing prepares the bootstrap application classes for native-image compilation
      And the native image configuration names the executable "magrathea-objectstorage"
      And the validation log must not contain Spring Boot's generated security password banner
      And native executable compilation succeeds with a Spring Boot 4 compatible GraalVM 25 native-image toolchain

    @REQ-PKG-002 @docker @alpine @jvm-free-runtime @implemented-and-validated
    Scenario: Native Docker packaging defines a JVM-free Alpine runtime image
      Given the native Docker build starts from a GraalVM 25 native-image builder with musl support
      And the Docker builder regenerates the documentation and admin UI static assets from source-controlled inputs
      When the builder compiles the bootstrap application with the "native,native-musl" Maven profiles
      Then the final runtime image is based on Alpine
      And the final runtime image contains the "magrathea-objectstorage" executable
      And the final runtime image does not install a JRE or JDK
      And runtime smoke validation confirms the container starts and the Admin API health endpoint is healthy
      And the final runtime image activates the storage-engine backend for single-node container deployments
      And runtime smoke validation confirms S3 ListBuckets XML and JSON plus bucket/object PUT/GET work without native reflection errors

  Rule: JVM Docker and CI packaging gates must avoid masked failures

    @REQ-PKG-003 @docker @jvm-runtime @root-dockerfile @implemented-and-validated
    Scenario: Root JVM Dockerfile builds the bootstrap JAR without masking Maven failures
      Given distribution source path "Dockerfile" defines the root JVM Docker image
      When maintainers run the validation mode "static Dockerfile packaging inspection"
      Then the Dockerfile runs the Gherkin appendix freshness gate before packaging
      And the Dockerfile regenerates documentation and admin UI static assets from source inputs
      And the Dockerfile builds the bootstrap application without Maven fail-never mode
      And the final runtime image is based on an Eclipse Temurin JRE image
      And the final runtime image exposes the S3 and Admin API ports
      And the final runtime image owns a writable application data directory as the non-root user
      And the final runtime image activates the storage-engine backend for single-node container deployments
      And the final runtime image starts the application with "java -jar /app.jar"

    @REQ-PKG-004 @ci @github-actions @implemented-not-e2e-validated
    Scenario: CI workflow wires source hygiene, Cucumber gates, and Docker packaging checks
      Given distribution source path ".github/workflows/ci.yml" defines the CI workflow
      When maintainers run the validation mode "static CI workflow inspection"
      Then the CI workflow uses Java "25"
      And the CI workflow checks the Gherkin appendix freshness
      And the CI workflow checks source hygiene
      And the CI workflow runs focused Cucumber validation for security, metadata durability, Phase 3 streaming, EP-5 operability and migration, and Phase 5 S3 semantics
      And the CI workflow builds required reactor dependency modules for the focused Maven gate
      And the CI workflow writes focused validation logs to an existing directory
      And the CI workflow builds and smokes the root JVM Docker image
      And the CI workflow requires Docker readiness to be ready in storage-engine mode
      And the CI workflow keeps native Docker packaging available as an explicit manual validation job
