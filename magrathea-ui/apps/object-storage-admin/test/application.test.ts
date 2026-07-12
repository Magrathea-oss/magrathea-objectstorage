import axe from 'axe-core'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminApiClientKey, createObjectStorageExtension, type AdminApiClient } from '@magrathea/object-storage-extension'
import App from '../src/App.vue'
import { applyDocumentTitle } from '../src/browser-adapters'

function client(): AdminApiClient {
  return {
    getHealth: vi.fn(), getLiveness: vi.fn(), getReadiness: vi.fn(), getBackendStatus: vi.fn(),
    listPolicies: vi.fn(), validatePolicy: vi.fn(), listDevices: vi.fn(),
    getDevice: vi.fn(async () => ({
      id: 'node-1-disk-0', storagePath: '/data/node-1/disk-0', health: 'DEGRADED',
      totalCapacityBytes: 107374182400, availableCapacityBytes: 26843545600,
      readEligible: true, writeEligible: false,
    })),
    listDiskSets: vi.fn(),
    getDiskSet: vi.fn(async () => ({ name: 'rack-a', failureDomain: 'RACK', devices: ['node-1-disk-0', 'node-2-disk-0'] })),
    getBucketCapacity: vi.fn(),
    configureBucketQuota: vi.fn(), getOperationalReport: vi.fn(),
    getPolicy: vi.fn(async () => ({
      storageClassId: 'MINIO_STANDARD',
      erasureCoding: { dataBlocks: 4, parityBlocks: 2 },
      replication: { factor: 1 },
      _links: {},
    })),
  } as AdminApiClient
}

async function mountApplication(path: string, props: Record<string, unknown> = {}) {
  const extensionRoutes = createObjectStorageExtension().routes!.map(({ route }) => route)
  const router = createRouter({ history: createMemoryHistory(), routes: extensionRoutes })
  await router.push(path)
  await router.isReady()
  const adminClient = client()
  const wrapper = mount(App, {
    attachTo: document.body,
    props,
    global: {
      plugins: [router],
      provide: { [adminApiClientKey as symbol]: adminClient },
    },
  })
  await flushPromises()
  return { wrapper, adminClient, router }
}

describe('deployable Object Storage Admin application', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it.each([
    {
      route: '/admin/storage-policies/minio-standard', resource: 'minio-standard', current: 'Storage policies',
      collectionPath: '/admin/storage-policies', collection: 'Storage policies', method: 'getPolicy', fixture: 'MINIO_STANDARD',
    },
    {
      route: '/admin/storage-devices/node-1-disk-0', resource: 'node-1-disk-0', current: 'Storage devices',
      collectionPath: '/admin/storage-devices', collection: 'Storage devices', method: 'getDevice', fixture: '/data/node-1/disk-0',
    },
    {
      route: '/admin/disk-sets/rack-a', resource: 'rack-a', current: 'Disk-set topology',
      collectionPath: '/admin/disk-sets', collection: 'Disk-set topology', method: 'getDiskSet', fixture: 'node-2-disk-0',
    },
  ])('restores $resource detail context from a copied URL without a dashboard visit', async ({
    route, resource, current, collectionPath, collection, method, fixture,
  }) => {
    const { wrapper, adminClient } = await mountApplication(route)

    expect(adminClient[method as keyof AdminApiClient]).toHaveBeenCalledWith(resource)
    expect(wrapper.get('main h1').text()).toBe(resource)
    expect(wrapper.text()).toContain(fixture)
    expect(wrapper.get('a[href="#main-content"]').text()).toContain('Skip')
    expect(wrapper.get('nav[aria-label="Primary navigation"] a[aria-current="page"] .product-shell__nav-label').text()).toBe(current)

    const breadcrumb = wrapper.get('nav[aria-label="Breadcrumb"]')
    expect(breadcrumb.get('a[href="/admin"]').text()).toBe('Dashboard')
    expect(breadcrumb.get(`a[href="${collectionPath}"]`).text()).toBe(collection)
    expect(breadcrumb.get('[aria-current="page"]').text()).toBe(resource)
  })

  it('binds task-grouped extension navigation and delegates appearance preference persistence', async () => {
    const saveAppearance = vi.fn()
    const { wrapper } = await mountApplication('/admin/storage-policies/minio-standard', {
      initialAppearance: 'system', appearancePreference: { save: saveAppearance },
    })

    const groupHeadings = wrapper.findAll('.product-shell__nav-section-heading h2').map((heading) => heading.text())
    expect(groupHeadings).toEqual(['Assess service health', 'Inspect storage', 'Administer configuration'])
    expect(wrapper.text()).toContain('Prioritize readiness and provider-backed operational evidence.')
    expect(wrapper.text()).toContain('Check a proposal without persisting it.')
    expect(wrapper.get('a[href="/admin/docs"] .product-shell__nav-label').text()).toBe('Documentation')

    await wrapper.get('select[aria-label="Appearance"]').setValue('dark')
    await flushPromises()
    expect(saveAppearance).toHaveBeenCalledWith('dark')
    expect(wrapper.get('.product-shell').attributes('data-appearance')).toBe('dark')
  })

  it('persists a locale change without losing the copied deep link and reports English page-title fallback', async () => {
    const save = vi.fn()
    const onLocaleApplied = vi.fn()
    const onPageTitleChange = vi.fn()
    const onEnglishFallback = vi.fn()
    const { wrapper, adminClient, router } = await mountApplication('/admin/storage-policies/minio-standard', {
      initialLocale: 'en', localePreference: { load: vi.fn(), save }, onLocaleApplied, onPageTitleChange, onEnglishFallback,
    })

    await wrapper.get('select[aria-label="Language"]').setValue('de')
    await flushPromises()

    expect(save).toHaveBeenCalledWith('de')
    expect(onLocaleApplied).toHaveBeenCalledWith(expect.objectContaining({ locale: 'de', documentLanguage: 'de' }))
    expect(onPageTitleChange).toHaveBeenLastCalledWith('Storage policy: minio-standard', 'de')
    expect(onEnglishFallback).toHaveBeenCalledWith(expect.objectContaining({
      locale: 'de', fallbackLocale: 'en', key: 'pages.policy-detail.title', outcome: 'fallback',
    }))
    expect(wrapper.get('select[aria-label="Sprache"]').attributes('aria-label')).toBe('Sprache')
    expect(wrapper.get('select[aria-label="Sprache"]').element.value).toBe('de')
    expect(wrapper.get('main h1').text()).toBe('minio-standard')
    expect(adminClient.getPolicy).toHaveBeenCalledWith('minio-standard')
    expect(router.currentRoute.value.fullPath).toBe('/admin/storage-policies/minio-standard')
  })

  it('keeps detail title, current navigation, and breadcrumbs synchronized across browser back and forward', async () => {
    const onPageTitleChange = (title: string) => applyDocumentTitle(document, title, 'Magrathea Object Storage')
    const { wrapper, router } = await mountApplication('/admin/storage-policies/minio-standard', { onPageTitleChange })

    expect(document.title).toBe('Storage policy: minio-standard — Magrathea Object Storage')
    await router.push('/admin/storage-devices/node-1-disk-0')
    await flushPromises()
    expect(document.title).toBe('Storage device: node-1-disk-0 — Magrathea Object Storage')
    expect(wrapper.get('nav[aria-label="Primary navigation"] a[aria-current="page"] .product-shell__nav-label').text()).toBe('Storage devices')
    const deviceBreadcrumb = wrapper.get('nav[aria-label="Breadcrumb"]')
    expect(deviceBreadcrumb.get('a[href="/admin/storage-devices"]').text()).toBe('Storage devices')
    expect(deviceBreadcrumb.get('[aria-current="page"]').text()).toBe('node-1-disk-0')

    await router.push('/admin/disk-sets/rack-a')
    await flushPromises()
    expect(document.title).toBe('Disk set: rack-a — Magrathea Object Storage')
    expect(wrapper.get('nav[aria-label="Primary navigation"] a[aria-current="page"] .product-shell__nav-label').text()).toBe('Disk-set topology')

    router.back()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/admin/storage-devices/node-1-disk-0')
    expect(document.title).toBe('Storage device: node-1-disk-0 — Magrathea Object Storage')
    expect(wrapper.get('nav[aria-label="Breadcrumb"] [aria-current="page"]').text()).toBe('node-1-disk-0')

    router.forward()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/admin/disk-sets/rack-a')
    expect(document.title).toBe('Disk set: rack-a — Magrathea Object Storage')
    expect(wrapper.get('nav[aria-label="Breadcrumb"] [aria-current="page"]').text()).toBe('rack-a')
  })

  it('renders a localized extension failure and retries it without changing route state', async () => {
    const failure = { extensionId: 'object-storage', status: 'error', messageKey: 'shell.extensions.loadError', messageParameters: { extension: 'Object Storage' }, recoveryAction: 'retry' }
    const ready = { extensionId: 'object-storage', status: 'ready' }
    const retry = vi.fn(async () => ({ navigation: [], extensionLoadStates: [ready] }))
    const composer = {
      snapshot: () => ({ navigation: [], extensionLoadStates: [failure] }),
      start: vi.fn(async () => ({ navigation: [], extensionLoadStates: [failure] })),
      retry,
    }
    const { wrapper } = await mountApplication('/admin/storage-policies/minio-standard', {
      initialLocale: 'de', extensionComposer: composer,
    })

    expect(wrapper.text()).toContain('Die Erweiterung Object Storage konnte nicht geladen werden.')
    const retryButton = wrapper.findAll('button').find((button) => button.text() === 'Erneut versuchen')!
    await retryButton.trigger('click')
    await flushPromises()

    expect(retry).toHaveBeenCalledWith('object-storage')
    expect(wrapper.text()).not.toContain('konnte nicht geladen werden')
    expect(wrapper.get('main h1').text()).toBe('minio-standard')
  })

  it('restores policy detail context from a direct route with landmarks, current navigation, and breadcrumbs', async () => {
    const { wrapper, adminClient } = await mountApplication('/admin/storage-policies/minio-standard')

    expect(adminClient.getPolicy).toHaveBeenCalledWith('minio-standard')
    expect(wrapper.get('main h1').text()).toBe('minio-standard')
    expect(wrapper.text()).toContain('MINIO_STANDARD')
    expect(wrapper.get('a[href="#main-content"]').text()).toContain('Skip')
    expect(wrapper.get('nav[aria-label="Primary navigation"] a[aria-current="page"] .product-shell__nav-label').text()).toBe('Storage policies')

    const breadcrumb = wrapper.get('nav[aria-label="Breadcrumb"]')
    expect(breadcrumb.get('a[href="/admin"]').text()).toBe('Dashboard')
    expect(breadcrumb.get('a[href="/admin/storage-policies"]').text()).toBe('Storage policies')
    expect(breadcrumb.text()).toContain('minio-standard')

    const result = await axe.run(wrapper.element, { rules: { 'color-contrast': { enabled: false } } })
    expect(result.violations).toEqual([])
  })
})
