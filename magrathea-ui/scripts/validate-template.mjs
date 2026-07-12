import { createHash } from 'node:crypto'
import { readdir, readFile, rm } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const workspaceRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const shellRoot = resolve(workspaceRoot, 'packages/product-shell')
const templateRoot = resolve(workspaceRoot, 'templates/product-app')
const templateDist = resolve(templateRoot, 'dist')

const readJson = async (path) => JSON.parse(await readFile(path, 'utf8'))
const shellPackage = await readJson(resolve(shellRoot, 'package.json'))
const templatePackage = await readJson(resolve(templateRoot, 'package.json'))
const templateManifest = await readJson(resolve(templateRoot, 'template.manifest.json'))

if (templateManifest.shellPackage !== shellPackage.name) {
  throw new Error('Template shell package does not match the reusable package name')
}
if (templateManifest.shellVersion !== shellPackage.version) {
  throw new Error('Template manifest does not match the reusable package version')
}
if (templatePackage.dependencies[shellPackage.name] !== shellPackage.version) {
  throw new Error('Template dependency does not match its deterministic manifest')
}

const forbiddenShellTerms = [
  '@magrathea/object-storage-extension',
  '/admin/',
  'storage-policies',
  'storage-devices',
  'disk-sets',
]
for (const sourceFile of ['src/index.ts', 'src/contracts.ts']) {
  const source = await readFile(resolve(shellRoot, sourceFile), 'utf8')
  for (const term of forbiddenShellTerms) {
    if (source.includes(term)) {
      throw new Error(`Product Shell source ${sourceFile} contains prohibited product knowledge: ${term}`)
    }
  }
}

const run = (args) => {
  const result = spawnSync('npm', args, { cwd: workspaceRoot, encoding: 'utf8', stdio: 'pipe' })
  if (result.status !== 0) {
    throw new Error([result.stdout, result.stderr].filter(Boolean).join('\n'))
  }
}

const hashDirectory = async (directory) => {
  const files = []
  const visit = async (current) => {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const path = resolve(current, entry.name)
      if (entry.isDirectory()) await visit(path)
      else files.push(path)
    }
  }
  await visit(directory)
  files.sort()

  const hash = createHash('sha256')
  for (const file of files) {
    hash.update(relative(directory, file))
    hash.update('\0')
    hash.update(await readFile(file))
    hash.update('\0')
  }
  return hash.digest('hex')
}

run(['run', 'build', '--workspace', '@magrathea/product-shell'])
await rm(templateDist, { recursive: true, force: true })
run(['run', 'build', '--workspace', '@magrathea/product-app-template'])
const firstDigest = await hashDirectory(templateDist)
await rm(templateDist, { recursive: true, force: true })
run(['run', 'build', '--workspace', '@magrathea/product-app-template'])
const secondDigest = await hashDirectory(templateDist)
await rm(templateDist, { recursive: true, force: true })

if (firstDigest !== secondDigest) {
  throw new Error(`Template builds are not deterministic: ${firstDigest} != ${secondDigest}`)
}

console.log(`Template validation passed: sha256:${firstDigest}`)
