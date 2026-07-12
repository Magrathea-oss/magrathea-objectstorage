import { describe, expect, it, vi } from 'vitest'
import {
  createApplicationStateStore,
  createLocalizationCatalog,
  defaultBrandTokens,
  defaultLocaleRegistrations,
  persistLocale,
  resolveBrandTokens,
  resolveInitialLocale,
  resolveProductIdentity,
  selectInitialLocale,
  shellEnglishBundle,
  standardPagePresentations,
} from '../src'

describe('product-neutral shell core contracts', () => {
  it('provides accessible identity and English state defaults', () => {
    expect(resolveProductIdentity()).toEqual({ name: 'Magrathea', accessibleName: 'Magrathea', mark: 'M' })
    expect(resolveProductIdentity({ identity: { name: 'Example' } }).accessibleName).toBe('Example')
    expect(resolveProductIdentity({ identity: { name: ' ', accessibleName: ' ', mark: undefined } })).toEqual({
      name: 'Magrathea', accessibleName: 'Magrathea', mark: 'M',
    })
    expect(resolveBrandTokens({ '--shell-brand': ' #123456 ' })).toEqual({
      ...defaultBrandTokens,
      '--shell-brand': '#123456',
    })
    expect(standardPagePresentations.loading.announcement).toBe('polite')
    expect(standardPagePresentations.error).toMatchObject({ announcement: 'assertive', recoveryAction: 'retry' })
    expect(Object.keys(standardPagePresentations)).toEqual([
      'loading',
      'empty',
      'error',
      'offline',
      'unavailable',
      'unauthorized',
      'not-found',
    ])
  })

  it('starts from complete product-neutral English messages and isolates extension namespaces', async () => {
    await expect(selectInitialLocale({ registeredLocales: ['de', 'en'] })).resolves.toBe('en')
    const catalog = createLocalizationCatalog([
      shellEnglishBundle,
      { locale: 'en', namespace: 'objectStorage', messages: { navigation: { policies: 'Storage policies' } } },
    ])
    expect(catalog.resolve('en', 'shell', 'navigation.label')).toBe('Primary navigation')
    for (const state of ['loading', 'empty', 'error', 'offline', 'unauthorized', 'not-found']) {
      expect(catalog.resolve('en', 'shell', `states.${state}.heading`)).not.toBe('Content unavailable')
    }
    expect(catalog.resolve('en', 'objectStorage', 'navigation.policies')).toBe('Storage policies')
    expect(catalog.resolve('en', 'shell', 'navigation.policies')).toBe('Content unavailable')
  })

  it('resolves extension messages with English fallback without exposing raw keys', () => {
    const missingMessage = vi.fn()
    const catalog = createLocalizationCatalog(
      [
        shellEnglishBundle,
        { locale: 'en', namespace: 'example', messages: { title: 'Example status' } },
        { locale: 'de', namespace: 'example', messages: {} },
      ],
      { reporter: { missingMessage } },
    )

    expect(catalog.resolve('de', 'example', 'title')).toBe('Example status')
    expect(catalog.resolve('de', 'example', 'private.raw.key')).toBe('Content unavailable')
    expect(missingMessage).toHaveBeenNthCalledWith(1, {
      locale: 'de',
      fallbackLocale: 'en',
      namespace: 'example',
      outcome: 'fallback',
    })
    expect(missingMessage).toHaveBeenNthCalledWith(2, {
      locale: 'de',
      fallbackLocale: 'en',
      namespace: 'example',
      outcome: 'unavailable',
    })
    expect(JSON.stringify(missingMessage.mock.calls)).not.toContain('private.raw.key')
  })

  it('selects localized locale metadata through a key-free persistence port', async () => {
    const save = vi.fn()
    const preference = { load: vi.fn(() => 'de'), save }
    expect(defaultLocaleRegistrations.map(({ locale, localizedName }) => [locale, localizedName])).toEqual([
      ['en', 'English'], ['de', 'Deutsch'], ['es', 'Español'], ['it', 'Italiano'], ['zh-CN', '简体中文'],
    ])

    await expect(resolveInitialLocale({ registrations: defaultLocaleRegistrations, preference })).resolves.toEqual({
      locale: 'de', localizedName: 'Deutsch', documentLanguage: 'de', source: 'saved',
    })
    await expect(
      selectInitialLocale({ registeredLocales: ['en', 'de'], preference }),
    ).resolves.toBe('de')
    await expect(persistLocale('de', ['en', 'de'], preference)).resolves.toBe('de')
    expect(preference.load).toHaveBeenCalledWith()
    expect(save).toHaveBeenCalledWith('de')
  })

  it('reports an unsupported saved locale and returns safe fallback metadata', async () => {
    const unsupportedSavedLocale = vi.fn()
    await expect(resolveInitialLocale({
      registrations: [
        { locale: 'en', localizedName: 'English' },
        { locale: 'de', localizedName: 'Deutsch' },
      ],
      defaultLocale: 'de',
      preference: { load: () => 'private.locale.key', save: vi.fn() },
      reporter: { unsupportedSavedLocale },
    })).resolves.toEqual({ locale: 'de', localizedName: 'Deutsch', documentLanguage: 'de', source: 'default' })
    expect(unsupportedSavedLocale).toHaveBeenCalledWith({
      savedLocale: 'private.locale.key', selectedLocale: 'de',
    })
  })

  it('keeps application state immutable and observable without API dependencies', () => {
    const store = createApplicationStateStore()
    const listener = vi.fn()
    const unsubscribe = store.subscribe(listener)

    const next = store.update((state) => ({ ...state, locale: 'de', navigationExpanded: true }))
    expect(next).toMatchObject({ locale: 'de', navigationExpanded: true })
    expect(Object.isFrozen(next)).toBe(true)
    expect(listener).toHaveBeenCalledOnce()

    unsubscribe()
    store.update((state) => ({ ...state, navigationExpanded: false }))
    expect(listener).toHaveBeenCalledOnce()
  })
})
