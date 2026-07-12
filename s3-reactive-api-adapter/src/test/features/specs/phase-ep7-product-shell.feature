@spec @phase-ep7 @product-shell @product-extension
Ability: Reuse the product-neutral Magrathea Product Shell
  Maintainers need a stable shell that composes independently owned Magrathea products
  without importing their domains. Product extensions own their routes, navigation,
  localization, permissions, API integrations, and screens while the shell supplies
  accessible presentation and lifecycle contracts.

  Rule: Product behavior enters only through an explicit extension contract

    @REQ-ADMIN-014 @functional-requirement @architecture @product-extension @static-architecture-required @implemented-and-validated
    Scenario: A minimal non-Object-Storage extension composes without shell edits
      Given a fixture extension with id "magrathea-example" contributes navigation label "Example", route "/example/status", permission "example:status:read", localization namespace "example", and a status screen
      When the frontend contract runner builds the shell with only extension "magrathea-example"
      Then the Example navigation entry and route are available
      And removing the extension removes its navigation, route, permission declaration, localization resources, and screen
      And no product-neutral shell source file changes between the two compositions

    @REQ-ADMIN-015 @functional-requirement @non-functional-requirement @architecture @product-neutral @static-architecture-required @implemented-and-validated
    Scenario: Product Shell source contains no Object Storage domain or endpoint knowledge
      Given the Product Shell and Object Storage Product Extension have separate source boundaries
      When the static architecture runner inspects Product Shell imports, routes, defaults, labels, models, clients, permissions, and endpoint constants
      Then the shell contains no bucket, object, multipart, storage policy, storage device, disk-set, S3, or storage-engine domain dependency
      And the shell contains no "/admin/storage-policies", "/admin/storage-devices", "/admin/disk-sets", "/admin/buckets", or S3 endpoint path
      And Object Storage navigation and documentation routes enter only through the registered Object Storage Product Extension

    @REQ-ADMIN-016 @functional-requirement @resilience @product-extension @frontend-contract-required @implemented-and-validated
    Scenario: A failed extension is isolated from the shell and other extensions
      Given extensions "object-storage" and "magrathea-example" are registered
      And extension "object-storage" fails during route composition
      When the shell starts
      Then the shell frame and extension "magrathea-example" remain usable
      And the shell shows a localized extension-load error naming "object-storage"
      And retrying the failed extension does not reload or duplicate healthy extension routes

  Rule: Design Tokens and Shell Primitives are product-neutral defaults

    @REQ-ADMIN-017 @functional-requirement @non-functional-requirement @design-tokens @theming @static-architecture-required @implemented-and-validated
    Scenario: Shell presentation is defined by reusable semantic Design Tokens
      Given the shell token contract defines color, typography, spacing, elevation, motion, focus, and responsive breakpoint tokens
      When the static architecture runner inspects shell primitives and the Object Storage extension
      Then shell primitives consume semantic tokens instead of Object Storage-specific visual constants
      And extension branding overrides only documented brand token slots
      And forced-colors and reduced-motion preferences retain readable content, visible focus, and operable navigation
      And missing brand overrides use the product-neutral Magrathea name, mark, and accessible text defaults

    @REQ-ADMIN-018 @functional-requirement @non-functional-requirement @shell-primitives @ui-states @accessibility @frontend-contract-required @implemented-and-validated
    Scenario Outline: Every asynchronous shell page uses the standard state contract
      Given fixture page "/example/resources" is rendered through the standard page and collection primitives
      When its resource provider enters state "<state>"
      Then the standard "<state>" presentation has a programmatic heading and status announcement
      And focus is not moved unexpectedly
      And the available recovery action is keyboard operable
      And no product-specific example value is presented as live data

      Examples:
        | state        |
        | loading      |
        | empty        |
        | error        |
        | offline      |
        | unauthorized |
        | not-found    |

  Rule: Localization and branding have safe defaults

    @REQ-ADMIN-019 @functional-requirement @non-functional-requirement @localization @branding @frontend-contract-required @implemented-and-validated
    Scenario: Shell starts with product-neutral English defaults and supports extension messages
      Given no saved locale, product name override, logo override, or Object Storage extension is configured
      When the Product Shell starts
      Then locale "en" is selected
      And the accessible product name is "Magrathea"
      And shell navigation, loading, empty, error, offline, unauthorized, and not-found messages are available in English
      And no Object Storage label appears
      When extension "object-storage" contributes its own English localization namespace
      Then its labels are resolved without modifying or shadowing product-neutral shell messages

    @REQ-ADMIN-020 @functional-requirement @non-functional-requirement @localization @accessibility @frontend-contract-required @implemented-and-validated
    Scenario: Locale selection persists without breaking navigation semantics
      Given locales "en", "de", "es", "it", and "zh-CN" are registered with localized names
      When a keyboard user selects locale "de" and reloads route "/admin/storage-policies/minio-standard"
      Then locale "de" remains selected
      And the document language is "de"
      And the current route, landmarks, accessible names, and focus order remain unchanged
      And any missing extension message falls back to English and is reported without exposing a raw translation key

  Rule: Shell navigation is accessible and responsive by contract

    @REQ-ADMIN-021 @functional-requirement @non-functional-requirement @accessibility @responsive @navigation @frontend-contract-required @implemented-and-validated
    Scenario Outline: Extension navigation adapts without losing keyboard operation
      Given extensions contribute 12 primary navigation entries
      And the shell viewport is <width> pixels wide
      When the user opens and traverses primary navigation using only the keyboard
      Then every entry is reachable in a logical order
      And the current entry is programmatically identified
      And collapsed navigation exposes its accessible name, expanded state, and Escape behavior
      And no navigation entry is clipped or requires horizontal page scrolling
      And focus returns to the navigation trigger when collapsed navigation closes

      Examples:
        | width |
        | 360   |
        | 768   |
        | 1440  |

  Rule: Frontend packaging proves shell reuse rather than copying templates

    @REQ-ADMIN-022 @functional-requirement @non-functional-requirement @build @packaging @template-reuse @docker-build-required @implemented-and-validated
    Scenario: Canonical Docker build composes two products from one shell source
      Given the canonical Docker frontend build starts from a clean checkout
      And product manifests "object-storage" and "magrathea-example" reference the same Product Shell package and different extension registrations
      When the Docker build produces both frontend distributions twice
      Then each distribution contains only its registered product routes and localization resources
      And both distributions identify the same Product Shell artifact version and content digest
      And no copied shell template or host-generated static asset is used as a source input
      And the second clean build produces byte-for-byte equivalent generated frontend and documentation assets
