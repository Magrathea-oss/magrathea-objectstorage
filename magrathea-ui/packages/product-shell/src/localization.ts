import type {
  LocalePreferencePort,
  LocaleRegistration,
  LocaleSelection,
  LocaleSelectionReporter,
  LocalizationBundle,
  LocalizationIssueReporter,
  LocalizationMessages,
} from './contracts'

export const defaultLocaleRegistrations: readonly LocaleRegistration[] = Object.freeze([
  Object.freeze({ locale: 'en', localizedName: 'English' }),
  Object.freeze({ locale: 'de', localizedName: 'Deutsch' }),
  Object.freeze({ locale: 'es', localizedName: 'Español' }),
  Object.freeze({ locale: 'it', localizedName: 'Italiano' }),
  Object.freeze({ locale: 'zh-CN', localizedName: '简体中文' }),
])

export const shellEnglishBundle: LocalizationBundle = Object.freeze({
  locale: 'en',
  namespace: 'shell',
  messages: Object.freeze({
    navigation: Object.freeze({ label: 'Primary navigation', open: 'Open navigation', close: 'Close navigation' }),
    extensions: Object.freeze({ loadError: 'The {extension} extension could not be loaded.' }),
    states: Object.freeze({
      loading: stateMessages('Loading', 'Content is loading.'),
      empty: stateMessages('Nothing here yet', 'No content is available.'),
      error: stateMessages('Something went wrong', 'The content could not be loaded.'),
      offline: stateMessages('You are offline', 'Reconnect and try again.'),
      unavailable: stateMessages('Temporarily unavailable', 'This content is currently unavailable.'),
      unauthorized: stateMessages('Access required', 'You do not have access to this content.'),
      'not-found': stateMessages('Page not found', 'The requested page could not be found.'),
    }),
    actions: Object.freeze({ retry: 'Try again', 'go-back': 'Go back', 'sign-in': 'Sign in' }),
  }),
})

export interface LocalizationCatalog {
  readonly locales: readonly string[]
  readonly bundles: ReadonlyMap<string, LocalizationMessages>
  resolve(locale: string, namespace: string, key: string): string
}

export function createLocalizationCatalog(
  bundles: readonly LocalizationBundle[],
  options: {
    readonly fallbackLocale?: string
    readonly reporter?: LocalizationIssueReporter
    readonly missingText?: string
  } = {},
): LocalizationCatalog {
  const fallbackLocale = options.fallbackLocale ?? 'en'
  const entries = new Map<string, LocalizationMessages>()
  for (const bundle of [...bundles].sort(compareBundles)) {
    const id = bundleId(bundle.locale, bundle.namespace)
    if (entries.has(id)) throw new Error(`Duplicate localization bundle: ${id}`)
    entries.set(id, bundle.messages)
  }
  const locales = Object.freeze([...new Set([...entries.keys()].map((key) => key.split(':', 1)[0]))].sort())

  return Object.freeze({
    locales,
    bundles: entries,
    resolve(locale: string, namespace: string, key: string): string {
      const localized = readMessage(entries.get(bundleId(locale, namespace)), key)
      if (localized !== undefined) return localized
      const fallback = readMessage(entries.get(bundleId(fallbackLocale, namespace)), key)
      options.reporter?.missingMessage({
        locale,
        fallbackLocale,
        namespace,
        outcome: fallback === undefined ? 'unavailable' : 'fallback',
      })
      return fallback ?? options.missingText ?? 'Content unavailable'
    },
  })
}

export async function resolveInitialLocale(options: {
  readonly registrations: readonly LocaleRegistration[]
  readonly defaultLocale?: string
  readonly preference?: LocalePreferencePort
  readonly reporter?: LocaleSelectionReporter
}): Promise<Readonly<LocaleSelection>> {
  const registrations = validateRegistrations(options.registrations)
  const byLocale = new Map(registrations.map((registration) => [registration.locale, registration]))
  const defaultRegistration = byLocale.get(options.defaultLocale ?? '')
  const fallbackRegistration = defaultRegistration ?? byLocale.get('en') ?? registrations[0] ?? {
    locale: 'en', localizedName: 'English', documentLanguage: 'en',
  }
  const saved = await options.preference?.load()
  const selected = saved ? byLocale.get(saved) : undefined
  if (saved && !selected) {
    options.reporter?.unsupportedSavedLocale({ savedLocale: saved, selectedLocale: fallbackRegistration.locale })
  }
  const registration = selected ?? fallbackRegistration
  return Object.freeze({
    locale: registration.locale,
    localizedName: registration.localizedName,
    documentLanguage: registration.documentLanguage ?? registration.locale,
    source: selected ? 'saved' : defaultRegistration ? 'default' : 'fallback',
  })
}

export async function selectInitialLocale(options: {
  readonly registeredLocales: readonly string[]
  readonly defaultLocale?: string
  readonly preference?: LocalePreferencePort
}): Promise<string> {
  const selection = await resolveInitialLocale({
    registrations: options.registeredLocales.map((locale) => ({ locale, localizedName: locale })),
    defaultLocale: options.defaultLocale,
    preference: options.preference,
  })
  return selection.locale
}

export async function persistLocale(
  locale: string,
  registeredLocales: readonly string[],
  preference?: LocalePreferencePort,
): Promise<string> {
  if (!registeredLocales.includes(locale)) throw new Error(`Locale is not registered: ${locale}`)
  await preference?.save(locale)
  return locale
}

function validateRegistrations(registrations: readonly LocaleRegistration[]): readonly LocaleRegistration[] {
  const seen = new Set<string>()
  return registrations.map((registration) => {
    if (!registration.locale.trim()) throw new Error('Locale registration requires a locale')
    if (!registration.localizedName.trim()) throw new Error(`Locale ${registration.locale} requires a localized name`)
    if (seen.has(registration.locale)) throw new Error(`Duplicate locale registration: ${registration.locale}`)
    seen.add(registration.locale)
    return Object.freeze({ ...registration })
  })
}

function stateMessages(heading: string, message: string): Readonly<Record<string, string>> {
  return Object.freeze({ heading, message })
}

function compareBundles(left: LocalizationBundle, right: LocalizationBundle): number {
  return left.locale.localeCompare(right.locale) || left.namespace.localeCompare(right.namespace)
}

function bundleId(locale: string, namespace: string): string {
  return `${locale}:${namespace}`
}

function readMessage(messages: LocalizationMessages | undefined, key: string): string | undefined {
  let value: unknown = messages
  for (const segment of key.split('.')) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
    value = (value as Readonly<Record<string, unknown>>)[segment]
  }
  return typeof value === 'string' ? value : undefined
}
