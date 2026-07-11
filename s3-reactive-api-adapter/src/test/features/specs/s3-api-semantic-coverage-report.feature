@spec @non-functional-requirement @documentation @s3-api @coverage-reporting
Ability: Evidence-based semantic coverage reporting for the official S3 API surface
  Maintainers need a deterministic operation-level matrix
  so the 111 mapped-route claim cannot be confused with complete S3 compatibility.

  Rule: Generated coverage separates route presence from semantic completion evidence

    @implemented-and-validated @REQ-DOC-001 @architecture @source-hygiene
    Scenario: The generated S3 API matrix classifies all 111 official operations without inventing completion
      Given the canonical S3 API inventory contains 111 distinct operations
      When the semantic coverage report is generated from router mappings and executable requirement tags
      Then every official operation has one matrix row
      And the report distinguishes mapped routes from implemented-and-validated semantic evidence
      And operations without explicit implemented-and-validated evidence remain pending
      And the committed S3 API coverage report is fresh
