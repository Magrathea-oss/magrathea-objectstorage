import { describe, expect, it, vi } from 'vitest'
import type { ProductExtension } from '../src'
import { createApplicationStateStore, createExtensionComposer, withProductComposition } from '../src'

const StatusScreen = { template: '<p>Status</p>' }

function exampleExtension(overrides: Partial<ProductExtension> = {}): ProductExtension {
  const id = overrides.id ?? 'magrathea-example'
  const slug = id === 'magrathea-example' ? 'example' : id
  return {
    id,
    navigation: [{ id: `${slug}-status`, labelKey: `${slug}.navigation.status`, route: `/${slug}/status` }],
    routes: [{ id: `${slug}-status`, route: { path: `/${slug}/status`, component: StatusScreen } }],
    permissions: [`${slug}:status:read`],
    capabilities: [{ id: `${slug}-status` }],
    localization: [{ locale: 'en', namespace: slug, messages: { navigation: { status: 'Example' } } }],
    ...overrides,
  }
}

describe('deterministic product extension composition', () => {
  it('adds and removes every contribution with the extension manifest', async () => {
    const composed = await createExtensionComposer([exampleExtension()]).start()
    const empty = await createExtensionComposer([]).start()

    expect(composed.navigation.map((entry) => entry.route)).toEqual(['/example/status'])
    expect(composed.routes.map((entry) => entry.route.path)).toEqual(['/example/status'])
    expect(composed.permissions).toEqual(['example:status:read'])
    expect(composed.localization.map((bundle) => bundle.namespace)).toEqual(['shell', 'example'])
    expect(empty.navigation).toEqual([])
    expect(empty.routes).toEqual([])
    expect(empty.permissions).toEqual([])
    expect(empty.localization.map((bundle) => bundle.namespace)).toEqual(['shell'])
  })

  it('composes task groups with navigation metadata and removes them with their extension', async () => {
    const extension = exampleExtension({
      navigationGroups: [{
        id: 'assess',
        labelKey: 'example.navigation.assess',
        descriptionKey: 'example.navigation.assessDescription',
        icon: 'overview',
      }],
      navigation: [{
        id: 'example-status',
        labelKey: 'example.navigation.status',
        descriptionKey: 'example.navigation.statusDescription',
        route: '/example/status',
        groupId: 'assess',
        icon: 'activity',
        status: { labelKey: 'example.navigation.attention', tone: 'warning' },
      }],
    })

    const composed = await createExtensionComposer([extension]).start()
    expect(composed.navigationGroups).toEqual(extension.navigationGroups)
    expect(composed.navigation[0]).toMatchObject({
      groupId: 'assess', icon: 'activity', status: { tone: 'warning' },
    })
    expect((await createExtensionComposer([]).start()).navigationGroups).toEqual([])
  })

  it('isolates a navigation entry that references an undeclared task group', async () => {
    const composed = await createExtensionComposer([exampleExtension({
      navigation: [{
        id: 'example-status', labelKey: 'example.navigation.status', route: '/example/status', groupId: 'missing',
      }],
    })]).start()

    expect(composed.navigation).toEqual([])
    expect(composed.failures).toEqual([{
      extensionId: 'magrathea-example',
      stage: 'contract',
      message: 'Unknown navigation group missing for navigation entry: example-status',
    }])
  })

  it('orders equal-priority extensions and contributions by stable identifiers', async () => {
    const later = exampleExtension({ id: 'zeta', navigation: [{ id: 'zeta', labelKey: 'zeta.label', route: '/zeta' }] })
    const earlier = exampleExtension({ id: 'alpha', navigation: [{ id: 'alpha', labelKey: 'alpha.label', route: '/alpha' }] })

    const first = await createExtensionComposer([later, earlier]).start()
    const second = await createExtensionComposer([earlier, later]).start()

    expect(first.extensions.map((extension) => extension.id)).toEqual(['alpha', 'zeta'])
    expect(first.navigation.map((entry) => entry.id)).toEqual(['alpha', 'zeta'])
    expect(first.navigation.map((entry) => entry.id)).toEqual(second.navigation.map((entry) => entry.id))
  })

  it('isolates setup failures and retries without reloading or duplicating healthy extensions', async () => {
    const healthySetup = vi.fn()
    let fail = true
    const failedSetup = vi.fn(() => {
      if (fail) throw new Error('fixture route composition failed')
    })
    const composer = createExtensionComposer([
      exampleExtension({ id: 'healthy', setup: healthySetup }),
      exampleExtension({ id: 'failed', setup: failedSetup }),
    ])

    const started = await composer.start()
    expect(started.extensions.map((extension) => extension.id)).toEqual(['healthy'])
    expect(started.failures).toEqual([
      { extensionId: 'failed', stage: 'setup', message: 'fixture route composition failed' },
    ])
    expect(started.extensionLoadStates).toEqual([
      {
        extensionId: 'failed',
        status: 'error',
        messageKey: 'shell.extensions.loadError',
        messageParameters: { extension: 'failed' },
        recoveryAction: 'retry',
      },
      { extensionId: 'healthy', status: 'ready' },
    ])
    const state = createApplicationStateStore()
    state.update((current) => withProductComposition(current, started))
    expect(state.get().extensionLoadStates[0]).toMatchObject({
      extensionId: 'failed', messageKey: 'shell.extensions.loadError', recoveryAction: 'retry',
    })

    fail = false
    const retried = await composer.retry('failed')
    expect(retried.extensions.map((extension) => extension.id)).toEqual(['failed', 'healthy'])
    expect(retried.routes).toHaveLength(2)
    expect(new Set(retried.routes.map(({ route }) => route.path)).size).toBe(2)
    expect(retried.extensionLoadStates).toEqual([
      { extensionId: 'failed', status: 'ready' },
      { extensionId: 'healthy', status: 'ready' },
    ])
    expect(healthySetup).toHaveBeenCalledTimes(1)
    expect(failedSetup).toHaveBeenCalledTimes(2)
  })

  it('filters contributions using host-provided flags and capabilities', async () => {
    const extension = exampleExtension({
      navigation: [
        {
          id: 'optional',
          labelKey: 'example.optional',
          route: '/example/optional',
          requiredFeatureFlags: ['optional-view'],
          requiredCapabilities: ['view-status'],
        },
      ],
    })

    const unavailable = await createExtensionComposer([extension]).start()
    const available = await createExtensionComposer([extension], {
      featureFlags: [{ id: 'optional-view', enabled: true }],
      capabilities: ['view-status'],
    }).start()

    expect(unavailable.navigation).toEqual([])
    expect(available.navigation).toHaveLength(1)
  })
})
