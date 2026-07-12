import { readFileSync, readdirSync } from 'node:fs'
import { extname, join, relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const workspaceRoot = resolve(process.cwd())
const productionRoots = ['apps', 'packages', 'templates'].map((directory) => join(workspaceRoot, directory))

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return entry.name === 'dist' || entry.name === 'test' ? [] : sourceFiles(path)
    return ['.js', '.ts', '.vue'].includes(extname(path)) ? [path] : []
  })
}

function imports(source: string): string[] {
  return [...source.matchAll(/(?:from\s+|import\s*\(|require\s*\()\s*['"]([^'"]+)['"]/g)]
    .map((match) => match[1])
}

describe('REQ-ADMIN-010 frontend observability boundary', () => {
  it('does not read storage-engine files or import backend and internal observability ports', () => {
    const backendImport = /(?:com\.example\.magrathea|storage-engine-(?:domain|reactive)|storageengine\/(?:application|domain|infrastructure)|(?:ReadPipelineObserver|S3SecurityAuditSink|BucketCapacityPort|ChunkStorePort|StorePort))|(?:^|\/)node:fs(?:\/|$)|(?:^|\/)fs(?:\/|$)/i
    const directFileRead = /\b(?:Deno\.readFile|Deno\.readTextFile|Bun\.file|showOpenFilePicker|showDirectoryPicker)\s*\(/
    const violations = productionRoots.flatMap(sourceFiles).flatMap((file) => {
      const source = readFileSync(file, 'utf8')
      return [
        ...imports(source).filter((specifier) => backendImport.test(specifier)).map((specifier) => `imports ${specifier}`),
        ...(directFileRead.test(source) ? ['uses a direct browser/runtime file-read API'] : []),
      ].map((violation) => `${relative(workspaceRoot, file)} ${violation}`)
    })

    expect(violations).toEqual([])
  })

  it('obtains audit, metric, and trace evidence only through the authorized Admin API report contract', () => {
    const clientSource = readFileSync(
      join(workspaceRoot, 'packages/object-storage-extension/src/adapters/admin-api-client.ts'),
      'utf8',
    )

    expect(clientSource).toContain("getOperationalReport: (report) => request<OperationalReport>('GET', `/admin/reports/${report}`)")
    expect(clientSource).not.toMatch(/(?:storage-engine|storageengine)\/(?:files|metrics|audit|traces)/i)
  })
})
