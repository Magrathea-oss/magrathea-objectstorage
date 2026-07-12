import { readFileSync, readdirSync } from 'node:fs'
import { extname, join, relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const shellRoot = resolve(process.cwd(), 'packages/product-shell')
const sourceRoot = join(shellRoot, 'src')

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? sourceFiles(path) : ['.ts', '.vue', '.css'].includes(extname(path)) ? [path] : []
  })
}

describe('REQ-ADMIN-015 Product Shell static architecture boundary', () => {
  it('contains no Object Storage package import, domain vocabulary, or endpoint knowledge in any shell source', () => {
    const prohibited = [
      /@magrathea\/object-storage-extension/i,
      /\b(bucket|multipart|storage[ -]?policy|storage[ -]?device|disk[ -]?set|storage[ -]?engine)\b/i,
      /\bS3\b/,
      /\/admin\/(?:storage-policies|storage-devices|disk-sets|buckets)/i,
    ]

    const violations = sourceFiles(sourceRoot).flatMap((file) => {
      const source = readFileSync(file, 'utf8')
      return prohibited
        .filter((pattern) => pattern.test(source))
        .map((pattern) => `${relative(shellRoot, file)} matched ${pattern}`)
    })

    expect(violations).toEqual([])
  })

  it('declares only product-neutral runtime dependencies', () => {
    const manifest = JSON.parse(readFileSync(join(shellRoot, 'package.json'), 'utf8')) as {
      dependencies?: Record<string, string>
      peerDependencies?: Record<string, string>
    }
    const dependencies = Object.keys({ ...manifest.dependencies, ...manifest.peerDependencies })
    expect(dependencies).not.toContain('@magrathea/object-storage-extension')
    expect(dependencies.sort()).toEqual(['vue', 'vue-i18n', 'vue-router'])
  })
})
