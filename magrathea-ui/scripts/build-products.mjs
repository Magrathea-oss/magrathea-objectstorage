import { createHash } from 'node:crypto'
import { readdir, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const workspace = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const shellDirectory = join(workspace, 'packages/product-shell')
const shellPackage = JSON.parse(await readFile(join(shellDirectory, 'package.json'), 'utf8'))
const products = [
  { app: 'apps/object-storage-admin', output: 'object-storage' },
  { app: 'apps/magrathea-example', output: 'magrathea-example' },
]

await rm(join(workspace, 'dist'), { recursive: true, force: true })
run('npm', ['run', 'build', '--workspace', '@magrathea/product-shell'])
run('npm', ['run', 'build', '--workspace', '@magrathea/magrathea-example'])
run('npm', ['run', 'build', '--workspace', '@magrathea/object-storage-extension'])

const shellDigest = await digestDirectory(join(shellDirectory, 'dist'))
const manifests = []
for (const product of products) {
  const applicationDirectory = join(workspace, product.app)
  const manifest = JSON.parse(await readFile(join(applicationDirectory, 'product.manifest.json'), 'utf8'))
  const applicationPackage = JSON.parse(await readFile(join(applicationDirectory, 'package.json'), 'utf8'))
  assert(manifest.productId === product.output, `Manifest productId does not match output: ${product.app}`)
  assert(manifest.applicationPackage === applicationPackage.name, `Manifest application package mismatch: ${product.app}`)
  assert(manifest.shellPackage === shellPackage.name, `Product does not reference ${shellPackage.name}: ${product.app}`)
  assert(applicationPackage.dependencies?.[shellPackage.name] === shellPackage.version, `Product shell dependency version mismatch: ${product.app}`)
  assert(!(await containsDirectory(applicationDirectory, 'product-shell')), `Copied Product Shell source found in ${product.app}`)
  manifests.push({ ...manifest, shellArtifact: { name: shellPackage.name, version: shellPackage.version, contentDigest: `sha256:${shellDigest}` } })
}

for (const manifest of manifests) {
  run('npm', ['run', 'build', '--workspace', manifest.applicationPackage])
  const outputDirectory = join(workspace, 'dist', manifest.productId)
  assert((await stat(outputDirectory)).isDirectory(), `Missing distribution: ${manifest.productId}`)
  await writeFile(join(outputDirectory, 'product-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
}

await validateIsolation(manifests)
console.log(`Built ${manifests.map(({ productId }) => productId).join(' and ')} with ${shellPackage.name}@${shellPackage.version} (sha256:${shellDigest}).`)

function run(command, args) {
  const result = spawnSync(command, args, { cwd: workspace, encoding: 'utf8', stdio: 'inherit' })
  if (result.status !== 0) process.exit(result.status ?? 1)
}

async function digestDirectory(directory) {
  const hash = createHash('sha256')
  for (const file of await files(directory)) {
    hash.update(relative(directory, file).replaceAll('\\', '/'))
    hash.update('\0')
    hash.update(await readFile(file))
    hash.update('\0')
  }
  return hash.digest('hex')
}

async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const result = []
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) result.push(...await files(path))
    else if (entry.isFile()) result.push(path)
  }
  return result
}

async function containsDirectory(directory, name) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && entry.name === name) return true
    if (entry.isDirectory() && await containsDirectory(join(directory, entry.name), name)) return true
  }
  return false
}

async function validateIsolation(manifests) {
  const [objectStorage, example] = manifests
  assert(objectStorage.extensionRegistrations.join() !== example.extensionRegistrations.join(), 'Products must register different extensions')
  assert(objectStorage.shellArtifact.contentDigest === example.shellArtifact.contentDigest, 'Products must identify the same shell digest')
  for (const manifest of manifests) {
    const foreign = manifests.find(({ productId }) => productId !== manifest.productId)
    const output = Buffer.concat(await Promise.all((await files(join(workspace, 'dist', manifest.productId))).map((file) => readFile(file)))).toString('utf8')
    for (const route of foreign.routes) assert(!output.includes(route), `${manifest.productId} contains foreign route ${route}`)
    for (const { namespace } of foreign.localization) assert(!output.includes(namespace), `${manifest.productId} contains foreign localization namespace ${namespace}`)
    assert(!(await files(join(workspace, 'dist', manifest.productId))).some((file) => /\.(?:ts|vue)$/.test(file)), `${manifest.productId} contains copied shell source`)
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
