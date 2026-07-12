@spec @ep-5 @operability @delivery @release
Ability: Reproducible single-node preview delivery
  As a maintainer and release operator,
  I want every supported image to come from a complete, version-coherent validation pipeline,
  So that the first single-node preview can be reproduced and replaced without losing committed data.

  Rule: Pull requests and release tags execute the canonical quality gate
    @REQ-OPS-022 @non-functional-requirement @ci @quality-gate @jvm @implemented-and-validated
    Scenario: CI runs the full Maven and generated-document gates before building the JVM image
      Given the canonical supported artifact is the Linux JVM 21 OCI image
      When a pull request or release tag runs the repository CI workflow
      Then the workflow executes the complete root Maven test gate without fail-never mode
      And the workflow checks the Gherkin appendix, S3 API coverage, and source hygiene for freshness
      And the JVM image build starts only after those mandatory gates pass
      And focused Cucumber jobs may fail fast but cannot replace the complete gate

  Rule: Release identity is coherent and immutable
    @REQ-OPS-023 @non-functional-requirement @release @versioning @oci @supply-chain @implemented-and-validated
    Scenario: A release tag produces one traceable single-node preview image
      Given the release policy declares semantic versioning and the supported single-node preview scope
      When tag "v0.1.0" starts the release workflow
      Then Maven release version, Git tag, OCI version label, and image version tag all equal "0.1.0"
      And the image records source revision, source URL, license, and creation time OCI labels
      And the registry receives immutable version, minor-line, and commit-SHA tags
      And the release records the image digest and declares the native image experimental

    @REQ-OPS-023 @non-functional-requirement @release @versioning @oci @docker-release-required @implemented-and-validated
    Scenario: Local registry rehearsal proves immutable preview publication semantics
      Given the canonical JVM image carries version "0.1.0" and the current source revision
      When the local release rehearsal publishes version, minor-line, and commit-SHA tags
      Then all three registry tags resolve to one non-empty immutable digest
      And a repeated publication attempt is refused rather than overwriting a tag
