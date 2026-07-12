import type {
  AppearancePreference,
  AppearancePreferencePort,
  LocalePreferencePort,
  LocaleRegistration,
  ResolvedAppearance,
  SystemAppearancePort,
} from '@magrathea/product-shell'
import type { Router } from 'vue-router'

export interface BrowserRuntimeConfiguration {
  readonly adminApiBaseUrl: string
  readonly s3DiagnosticsBaseUrl?: string
}

export interface BrowserConnectivity {
  isOnline(): boolean
  subscribe(listener: (online: boolean) => void): () => void
}

const LOCALE_PATTERN = /^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-\d{3})?$/
const APPEARANCES = new Set<AppearancePreference>(['system', 'light', 'dark'])
const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)'

type StorageAccess = Pick<Storage, 'getItem' | 'setItem'>

interface MatchMediaResult {
  readonly matches: boolean
  addEventListener?(type: 'change', listener: (event: { readonly matches: boolean }) => void): void
  removeEventListener?(type: 'change', listener: (event: { readonly matches: boolean }) => void): void
  addListener?(listener: (event: { readonly matches: boolean }) => void): void
  removeListener?(listener: (event: { readonly matches: boolean }) => void): void
}

interface MatchMediaRuntime {
  matchMedia?(query: string): MatchMediaResult
}

/** Accesses localStorage without failing startup in restricted browser contexts. */
export function getBrowserLocalStorage(runtime: { readonly localStorage?: Storage }): Storage | undefined {
  try {
    return runtime.localStorage
  } catch {
    return undefined
  }
}

/** Reads public endpoint configuration from the served document. Never reads credentials. */
export function readBrowserRuntimeConfiguration(
  document: Document,
  location: Location,
): BrowserRuntimeConfiguration {
  const adminApiBaseUrl = readEndpointMeta(document, location, 'magrathea-admin-api-base-url', '/')
  const s3Value = document.querySelector<HTMLMetaElement>('meta[name="magrathea-s3-diagnostics-base-url"]')
    ?.content.trim()

  return Object.freeze({
    adminApiBaseUrl,
    ...(s3Value ? { s3DiagnosticsBaseUrl: readEndpointMeta(
      document,
      location,
      'magrathea-s3-diagnostics-base-url',
      undefined,
    ) } : {}),
  })
}

export function createBrowserConnectivity(
  window: Pick<Window, 'addEventListener' | 'removeEventListener'>,
  navigator: Pick<Navigator, 'onLine'>,
): BrowserConnectivity {
  return Object.freeze({
    isOnline: () => navigator.onLine !== false,
    subscribe(listener: (online: boolean) => void) {
      const notify = () => listener(navigator.onLine !== false)
      window.addEventListener('online', notify)
      window.addEventListener('offline', notify)
      return () => {
        window.removeEventListener('online', notify)
        window.removeEventListener('offline', notify)
      }
    },
  })
}

export function createLocalStorageLocalePreference(
  storage: StorageAccess | undefined,
  key = 'magrathea.locale',
): LocalePreferencePort {
  return Object.freeze({
    load() {
      try {
        const locale = storage?.getItem(key)
        return locale && LOCALE_PATTERN.test(locale) ? locale : undefined
      } catch {
        return undefined
      }
    },
    save(locale: string) {
      if (!LOCALE_PATTERN.test(locale)) throw new TypeError('Locale must be a valid language tag')
      try {
        storage?.setItem(key, locale)
      } catch {
        // Persistence can be unavailable in private or policy-restricted browser contexts.
      }
    },
  })
}

/** Persists only the public appearance enum and degrades to in-memory behavior. */
export function createLocalStorageAppearancePreference(
  storage: StorageAccess | undefined,
  key = 'magrathea.appearance',
): AppearancePreferencePort {
  return Object.freeze({
    load() {
      try {
        const preference = storage?.getItem(key)
        return isAppearancePreference(preference) ? preference : undefined
      } catch {
        return undefined
      }
    },
    save(preference: AppearancePreference) {
      if (!isAppearancePreference(preference)) throw new TypeError('Appearance must be system, light, or dark')
      try {
        storage?.setItem(key, preference)
      } catch {
        // The visible selection still works when persistence is blocked or unavailable.
      }
    },
  })
}

/** Adapts prefers-color-scheme without assuming matchMedia is present or usable. */
export function createMatchMediaSystemAppearance(runtime?: MatchMediaRuntime): SystemAppearancePort {
  let query: MatchMediaResult | undefined
  try {
    query = runtime?.matchMedia?.(DARK_SCHEME_QUERY)
  } catch {
    query = undefined
  }

  const current = (): ResolvedAppearance => {
    try {
      return query?.matches ? 'dark' : 'light'
    } catch {
      return 'light'
    }
  }

  return Object.freeze({
    current,
    subscribe(listener: (appearance: ResolvedAppearance) => void) {
      if (!query) return () => undefined
      const notify = (event: { readonly matches: boolean }) => listener(event.matches ? 'dark' : 'light')
      try {
        if (query.addEventListener && query.removeEventListener) {
          query.addEventListener('change', notify)
          return () => query?.removeEventListener?.('change', notify)
        }
        if (query.addListener && query.removeListener) {
          query.addListener(notify)
          return () => query?.removeListener?.(notify)
        }
      } catch {
        // Media-query observation is optional; retain the last safe appearance.
      }
      return () => undefined
    },
  })
}

export interface DocumentAppearanceSynchronizer {
  apply(preference: AppearancePreference): void
  dispose(): void
}

/** Keeps browser chrome/native controls aligned with the shell's effective appearance. */
export function synchronizeDocumentAppearance(
  document: Document,
  systemAppearance: SystemAppearancePort,
  initialPreference: AppearancePreference,
): DocumentAppearanceSynchronizer {
  let preference = isAppearancePreference(initialPreference) ? initialPreference : 'system'
  const synchronize = () => {
    const resolved = preference === 'system' ? systemAppearance.current() : preference
    document.documentElement.dataset.appearance = preference
    document.documentElement.dataset.resolvedAppearance = resolved
    document.documentElement.style.colorScheme = resolved
  }
  const unsubscribe = systemAppearance.subscribe?.(() => {
    if (preference === 'system') synchronize()
  }) ?? (() => undefined)

  synchronize()
  return Object.freeze({
    apply(next: AppearancePreference) {
      if (!isAppearancePreference(next)) return
      preference = next
      synchronize()
    },
    dispose: unsubscribe,
  })
}

export function applyDocumentLocale(document: Document, registration: LocaleRegistration): void {
  document.documentElement.lang = registration.documentLanguage ?? registration.locale
}

export function adminDocumentTitleForRoute(route: Router['currentRoute']['value']): string {
  const kind = String(route.meta.kind || '')
  const id = String(route.params.id || '')
  if (kind === 'policy-detail') return `Storage policy: ${id || 'unknown'}`
  if (kind === 'device-detail') return `Storage device: ${id || 'unknown'}`
  if (kind === 'disk-set-detail') return `Disk set: ${id || 'unknown'}`
  if (kind === 'capacity-detail') return `Bucket capacity: ${String(route.params.bucket || 'unknown')}`
  return ({
    dashboard: 'Admin dashboard',
    backend: 'Backend status',
    policies: 'Storage policies',
    'policy-validation': 'Validate a policy proposal',
    devices: 'Storage devices',
    'disk-sets': 'Disk-set topology',
    capacity: 'Bucket capacity lookup',
    'data-hygiene': 'Data hygiene',
    observability: 'Observability',
    diagnostics: 'S3 HeadObject diagnostic',
    documentation: 'Documentation',
  } as Readonly<Record<string, string>>)[kind] || 'Object Storage administration'
}

export function applyDocumentTitle(document: Document, pageTitle: string, productName: string): void {
  const title = pageTitle.trim()
  document.title = title ? `${title} — ${productName}` : productName
}

export function installBrowserNavigationEffects(options: {
  readonly router: Router
  readonly document: Document
  readonly productName: string
  readonly titleForRoute: (route: Router['currentRoute']['value']) => string
}): () => void {
  const removeHook = options.router.afterEach((route, from) => {
    applyDocumentTitle(options.document, options.titleForRoute(route), options.productName)

    // Keep initial browser focus intact; move focus only after in-app navigation.
    if (from.matched.length > 0 && route.fullPath !== from.fullPath) {
      queueMicrotask(() => options.document.querySelector<HTMLElement>('#main-content')?.focus())
    }
  })
  return removeHook
}

function isAppearancePreference(value: unknown): value is AppearancePreference {
  return typeof value === 'string' && APPEARANCES.has(value as AppearancePreference)
}

function readEndpointMeta(
  document: Document,
  location: Location,
  name: string,
  fallback: string | undefined,
): string {
  const configured = document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.content.trim() || fallback
  if (!configured) throw new TypeError(`Runtime endpoint ${name} is not configured`)
  const url = new URL(configured, location.href)
  if (!['http:', 'https:'].includes(url.protocol)) throw new TypeError(`Runtime endpoint ${name} must use HTTP or HTTPS`)
  if (url.username || url.password || url.search || url.hash) {
    throw new TypeError(`Runtime endpoint ${name} must not contain credentials, a query, or a fragment`)
  }
  return url.toString()
}
