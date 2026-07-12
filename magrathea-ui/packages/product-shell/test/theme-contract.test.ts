import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { brandTokenSlots, defaultBrandTokens } from '../src'

const theme = readFileSync(resolve(process.cwd(), 'packages/product-shell/src/theme.css'), 'utf8')
const productShell = readFileSync(resolve(process.cwd(), 'packages/product-shell/src/components/ProductShell.vue'), 'utf8')

describe('default shell visual contract', () => {
  it('defines each semantic token family required by the shell specification', () => {
    for (const token of [
      '--shell-brand',
      '--shell-surface',
      '--shell-text',
      '--shell-font-sans',
      '--shell-space-4',
      '--shell-shadow',
      '--shell-motion-normal',
      '--shell-focus-ring',
      '--shell-breakpoint-compact',
    ]) expect(theme).toContain(token)
  })

  it('documents only the four supported branding override slots and keeps CSS defaults aligned', () => {
    expect(brandTokenSlots).toEqual([
      '--shell-brand-strong', '--shell-brand', '--shell-brand-accent', '--shell-brand-soft',
    ])
    for (const slot of brandTokenSlots) expect(theme).toContain(`${slot}: ${defaultBrandTokens[slot]};`)
  })

  it('retains visible focus and removes non-essential motion for user preferences', () => {
    expect(theme).toContain(':focus-visible')
    expect(theme).toContain('@media (forced-colors: active)')
    expect(theme).toContain('@media (prefers-reduced-motion: reduce)')
    expect(productShell).toContain('@media (forced-colors: active)')
    expect(productShell).toContain('@media (prefers-reduced-motion: reduce)')
  })

  it('supports controlled light, dark, and system appearance without fonts or network assets', () => {
    expect(theme).toContain("[data-appearance='dark']")
    expect(theme).toContain("[data-appearance='system']")
    expect(theme).toContain('@media (prefers-color-scheme: dark)')
    expect(theme).not.toMatch(/@import|url\(/)
    expect(productShell).toContain(':data-appearance="appearance"')
  })

  it('uses restrained midnight depth and semantic accents rather than low-contrast glass', () => {
    expect(theme).toContain('--shell-inverse-surface: #111827')
    expect(theme).toContain('--shell-accent-violet')
    expect(theme).toContain('--shell-accent-teal')
    expect(theme).toContain('--shell-accent-orange')
    expect(productShell).toContain('background: #101725')
    expect(productShell).not.toContain('backdrop-filter')
  })

  it('defines compact, 768px, and wide navigation layouts without horizontal page overflow', () => {
    expect(productShell).toContain('overflow-x: clip')
    expect(productShell).toContain('width: min(20rem, 88vw)')
    expect(productShell).toContain('@media (min-width: 48rem)')
    expect(productShell).toContain('@media (min-width: 75rem)')
    expect(productShell).toContain('overflow-y: auto')
    expect(productShell).toContain('visibility: hidden')
    expect(productShell).toContain('visibility: visible')
  })
})
