import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import {
  adminDocumentTitleForRoute,
  applyDocumentLocale,
  applyDocumentTitle,
  createBrowserConnectivity,
  createLocalStorageLocalePreference,
  installBrowserNavigationEffects,
  readBrowserRuntimeConfiguration,
} from '../src/browser-adapters'

describe('Object Storage Admin browser adapters', () => {
  it('reads only public, separately configured runtime endpoints', () => {
    document.head.innerHTML = `
      <meta name="magrathea-admin-api-base-url" content="https://admin.example.test/control">
      <meta name="magrathea-s3-diagnostics-base-url" content="https://s3.example.test/data">
    `

    expect(readBrowserRuntimeConfiguration(document, window.location)).toEqual({
      adminApiBaseUrl: 'https://admin.example.test/control',
      s3DiagnosticsBaseUrl: 'https://s3.example.test/data',
    })

    document.head.innerHTML = '<meta name="magrathea-admin-api-base-url" content="https://user:secret@admin.example.test/">'
    expect(() => readBrowserRuntimeConfiguration(document, window.location)).toThrow(/must not contain credentials/)
  })

  it('persists only a locale tag and tolerates unavailable storage', () => {
    const values = new Map<string, string>()
    const preference = createLocalStorageLocalePreference({
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => { values.set(key, value) },
      removeItem: (key) => { values.delete(key) },
    })

    preference.save('de')
    expect(preference.load()).toBe('de')
    expect(values).toEqual(new Map([['magrathea.locale', 'de']]))
    expect(() => preference.save('de?token=secret')).toThrow(/valid language tag/)

    const unavailable = createLocalStorageLocalePreference({
      getItem: () => { throw new DOMException('blocked') },
      setItem: () => { throw new DOMException('blocked') },
      removeItem: vi.fn(),
    })
    expect(unavailable.load()).toBeUndefined()
    expect(() => unavailable.save('en')).not.toThrow()
  })

  it('applies registered document language metadata and a safe product title', () => {
    applyDocumentLocale(document, { locale: 'zh-CN', localizedName: '简体中文', documentLanguage: 'zh-CN' })
    applyDocumentTitle(document, 'Storage policies', 'Magrathea Object Storage')

    expect(document.documentElement.lang).toBe('zh-CN')
    expect(document.title).toBe('Storage policies — Magrathea Object Storage')
  })

  it.each([
    ['policy-detail', 'minio-standard', 'Storage policy: minio-standard'],
    ['device-detail', 'node-1-disk-0', 'Storage device: node-1-disk-0'],
    ['disk-set-detail', 'rack-a', 'Disk set: rack-a'],
  ])('creates a meaningful document title for the %s route', (kind, id, expected) => {
    expect(adminDocumentTitleForRoute({ meta: { kind }, params: { id } } as never)).toBe(expected)
  })

  it('reports browser online transitions and detaches its listeners', () => {
    let online = true
    const listeners = new Map<string, EventListener>()
    const connectivity = createBrowserConnectivity({
      addEventListener: (name, listener) => { listeners.set(name, listener as EventListener) },
      removeEventListener: (name) => { listeners.delete(name) },
    } as Pick<Window, 'addEventListener' | 'removeEventListener'>, {
      get onLine() { return online },
    })
    const observed: boolean[] = []
    const unsubscribe = connectivity.subscribe((value) => observed.push(value))

    online = false
    listeners.get('offline')?.(new Event('offline'))
    online = true
    listeners.get('online')?.(new Event('online'))
    expect(observed).toEqual([false, true])

    unsubscribe()
    expect(listeners.size).toBe(0)
  })

  it('updates document title and focuses main content after client-side navigation only', async () => {
    document.body.innerHTML = '<main id="main-content" tabindex="-1"></main>'
    const main = document.querySelector<HTMLElement>('main')!
    const focus = vi.spyOn(main, 'focus')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/admin', component: { template: '<div />' }, meta: { kind: 'Dashboard' } },
        { path: '/admin/storage-policies/:id', component: { template: '<div />' } },
      ],
    })
    installBrowserNavigationEffects({
      router,
      document,
      productName: 'Magrathea Object Storage',
      titleForRoute: (route) => String(route.params.id || route.meta.kind),
    })

    await router.push('/admin')
    expect(document.title).toBe('Dashboard — Magrathea Object Storage')
    expect(focus).not.toHaveBeenCalled()

    await router.push('/admin/storage-policies/minio-standard')
    await new Promise(queueMicrotask)
    expect(document.title).toBe('minio-standard — Magrathea Object Storage')
    expect(focus).toHaveBeenCalledOnce()
  })
})
