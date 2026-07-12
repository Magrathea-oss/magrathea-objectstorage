# Magrathea Product Shell

`@magrathea/product-shell` is the reusable, product-neutral core for Magrathea web applications.

The package provides:

- accessible product identity with Magrathea defaults;
- explicit navigation and Vue route registration contracts;
- feature-flag, capability, permission, and localization contributions;
- deterministic extension ordering, conflict detection, failure isolation, and retry;
- API-independent application and asynchronous resource-state contracts;
- English shell messages and loading, empty, error, offline, unavailable, unauthorized, and not-found presentation metadata;
- ports for locale persistence and missing-message reporting;
- a responsive application frame, skip link, identity, navigation, breadcrumbs, and page header;
- reusable cards, badges, banners, data tables, loading skeletons, page states, fields, and modal dialogs;
- semantic theme tokens with visible focus, forced-colors, reduced-motion, and compact-layout support.

Applications own platform adapters and register independently maintained product extensions. Extensions own their screens, domain models, routes, integrations, and localization namespaces. The shell core does not access browser storage, networks, native runtimes, or product APIs directly.

## Minimal composition

```ts
import { createExtensionComposer, type ProductExtension } from '@magrathea/product-shell'

const extension: ProductExtension = {
  id: 'magrathea-example',
  navigation: [{ id: 'status', labelKey: 'example.status', route: '/example/status' }],
  routes: [{ id: 'status', route: { path: '/example/status', component: StatusPage } }],
  localization: [{ locale: 'en', namespace: 'example', messages: { status: 'Example' } }],
}

const composition = await createExtensionComposer([extension]).start()
```

Composition is stable by explicit `order` and then identifier. An extension cannot write to the reserved `shell` localization namespace. A failed extension is reported in `composition.failures`; healthy contributions remain available and are not reinitialized when the failed extension is retried. `composition.extensionLoadStates` is the presentation-safe shell state: failures use `shell.extensions.loadError`, an extension-name parameter, and the `retry` recovery action. Apply it to `ApplicationState` with `withProductComposition`; do not present the technical failure message.

## Localization

Register locales with `LocaleRegistration`, including their localized display names. `defaultLocaleRegistrations` supplies English, Deutsch, Español, Italiano, and 简体中文 for `en`, `de`, `es`, `it`, and `zh-CN`. `resolveInitialLocale` returns locale, localized name, document language, and selection source. Persistence is provided through `LocalePreferencePort`; its adapter owns any browser storage key, while the shell exchanges only locale values. Missing messages fall back to English and report namespace-level outcomes without exposing raw translation keys.

## Presentation

Import the default semantic theme once at the application entry point:

```ts
import '@magrathea/product-shell/theme.css'
```

Use `ProductShell` as the page frame and compose the exported `Shell*` primitives inside it. Pass `extensionLoadStates` from the composition contract and handle `retry-extension` by invoking the composer; the rendered alert names only the extension and never exposes the technical failure. Pass registered `locales` and `selectedLocale`, then handle `locale-change` in the application adapter to update localization, document language, and persistence. The native selector retains platform keyboard behavior and does not replace or nest the shell's navigation landmarks.

Product branding may override only `--shell-brand-strong`, `--shell-brand`, `--shell-brand-accent`, and `--shell-brand-soft`; `brandTokenSlots`, `defaultBrandTokens`, and `resolveBrandTokens` are the executable contract. Overrides can be passed through the `brandTokens` prop or set on an application container. All other semantic tokens remain shell-owned. Missing identity or token overrides render the product-neutral Magrathea name, `M` mark, accessible home-link name, and default palette. A logo that cannot load falls back to the same text mark; logos are decorative because the adjacent product name and link accessible name provide the identity. Extensions should consume semantic tokens rather than defining shell-level visual constants. The primitives contain no product routes, product data, API integration, persistence, or document mutation.
