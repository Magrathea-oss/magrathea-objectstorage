import { describe, expect, it } from 'vitest'
import { productShellArtifact } from '../src'

describe('product shell package scaffold', () => {
  it('exposes stable package identity for composed distributions', () => {
    expect(productShellArtifact).toEqual({
      name: '@magrathea/product-shell',
      version: '0.1.0',
    })
  })
})
