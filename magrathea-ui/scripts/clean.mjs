import { rm } from 'node:fs/promises'

const generatedDirectories = [
  new URL('../dist', import.meta.url),
  new URL('../packages/product-shell/dist', import.meta.url),
  new URL('../packages/magrathea-example/dist', import.meta.url),
  new URL('../packages/object-storage-extension/dist', import.meta.url),
  new URL('../templates/product-app/dist', import.meta.url),
  new URL('../playwright-report', import.meta.url),
  new URL('../test-results', import.meta.url),
]

await Promise.all(generatedDirectories.map((directory) => rm(directory, { force: true, recursive: true })))
