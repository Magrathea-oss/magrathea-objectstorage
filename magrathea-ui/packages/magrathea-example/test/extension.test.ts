import { describe, expect, it } from 'vitest'
import { createExtensionComposer } from '@magrathea/product-shell'
import { magratheaExampleExtension } from '../src'

describe('magrathea-example extension fixture', () => {
  it('adds and removes its complete contract without shell changes', async () => {
    const composed = await createExtensionComposer([magratheaExampleExtension]).start()
    const removed = await createExtensionComposer([]).start()

    expect(composed.navigation).toEqual([
      expect.objectContaining({ labelKey: 'example.navigation.status', route: '/example/status' }),
    ])
    expect(composed.routes.map(({ route }) => route.path)).toEqual(['/example/status'])
    expect(composed.permissions).toEqual(['example:status:read'])
    expect(composed.localization.map(({ namespace }) => namespace)).toEqual(['shell', 'example'])
    expect(removed).toMatchObject({ navigation: [], routes: [], permissions: [] })
    expect(removed.localization.map(({ namespace }) => namespace)).toEqual(['shell'])
  })
})
