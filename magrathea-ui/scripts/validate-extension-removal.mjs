import { createHash } from 'node:crypto'
import { mkdtemp, mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join, relative, resolve } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
import { build } from 'vite'

const workspace = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const shellRoot = join(workspace, 'packages/product-shell')
const exampleRoot = join(workspace, 'packages/magrathea-example')
const temporaryRoot = await mkdtemp(join(tmpdir(), 'magrathea-req-admin-014-'))
const generatedDirectories = [
  join(workspace, 'dist'),
  join(shellRoot, 'dist'),
  join(exampleRoot, 'dist'),
  join(workspace, 'packages/object-storage-extension/dist'),
  join(workspace, 'templates/product-app/dist'),
]

try {
  await Promise.all(generatedDirectories.map((directory) => rm(directory, { recursive: true, force: true })))
  const sourceDigestBefore = await digestDirectory(join(shellRoot, 'src'))

  run(['run', 'build', '--workspace', '@magrathea/product-shell'])
  run(['run', 'build', '--workspace', '@magrathea/magrathea-example'])

  const shellArtifactDigest = await digestDirectory(join(shellRoot, 'dist'))
  const withExtension = await buildComposition('with-extension', true)
  const sourceDigestAfterExtension = await digestDirectory(join(shellRoot, 'src'))
  assert(sourceDigestAfterExtension === sourceDigestBefore, 'Product Shell source changed while composing the example extension')
  assertExampleContributions(withExtension)

  const withoutExtension = await buildComposition('without-extension', false)
  const sourceDigestAfterRemoval = await digestDirectory(join(shellRoot, 'src'))
  assert(sourceDigestAfterRemoval === sourceDigestBefore, 'Product Shell source changed after removing the example extension')
  assertNoExampleContributions(withoutExtension)

  console.log('REQ-ADMIN-014 extension removal validation passed.')
  console.log(`Product Shell artifact: sha256:${shellArtifactDigest}`)
  console.log(`Product Shell source before/with/without: sha256:${sourceDigestBefore}`)
  console.log(`Composition with extension: sha256:${withExtension.bundleDigest}`)
  console.log(`Composition without extension: sha256:${withoutExtension.bundleDigest}`)
  console.log('Validated contributions: navigation, route, permission, localization, and ExampleStatusScreen.')
  console.log('Validated removal: all magrathea-example contributions are absent.')
} finally {
  await Promise.all([
    rm(temporaryRoot, { recursive: true, force: true }),
    ...generatedDirectories.map((directory) => rm(directory, { recursive: true, force: true })),
  ])
}

async function buildComposition(name, includeExtension) {
  const root = join(temporaryRoot, name)
  const output = join(root, 'dist')
  await mkdir(root, { recursive: true })
  await writeFile(join(root, 'package.json'), '{"type":"module"}\n')
  await writeFile(join(root, 'index.html'), '<script type="module" src="/main.js"></script>\n')
  await writeFile(join(root, 'main.js'), compositionEntry(includeExtension))
  await build({
    configFile: false,
    root,
    logLevel: 'silent',
    build: {
      outDir: output,
      emptyOutDir: true,
      target: 'esnext',
      modulePreload: false,
      minify: false,
      sourcemap: false,
    },
  })

  const javaScript = (await files(output)).filter((file) => file.endsWith('.js'))
  assert(javaScript.length === 1, `${name} must produce exactly one JavaScript composition artifact`)
  delete globalThis.__REQ_ADMIN_014__
  await import(`${pathToFileURL(javaScript[0]).href}?digest=${await digestDirectory(output)}`)
  const evidence = globalThis.__REQ_ADMIN_014__
  delete globalThis.__REQ_ADMIN_014__
  assert(evidence, `${name} did not publish composition evidence`)
  return { ...evidence, bundleDigest: await digestDirectory(output) }
}

function compositionEntry(includeExtension) {
  const shellImport = pathToFileURL(join(shellRoot, 'dist/index.js')).href
  const exampleImport = pathToFileURL(join(exampleRoot, 'dist/index.js')).href
  const extensionImport = includeExtension
    ? `import { magratheaExampleExtension } from ${JSON.stringify(exampleImport)}\n`
    : ''
  const extensions = includeExtension ? '[magratheaExampleExtension]' : '[]'
  return `import { createExtensionComposer } from ${JSON.stringify(shellImport)}
${extensionImport}const composition = await createExtensionComposer(${extensions}).start()
globalThis.__REQ_ADMIN_014__ = {
  extensionIds: composition.extensions.map(({ id }) => id),
  navigation: composition.navigation.map(({ labelKey, route, permission }) => ({ labelKey, route, permission })),
  routes: composition.routes.map(({ route }) => route.path),
  permissions: [...composition.permissions],
  localization: composition.localization
    .filter(({ namespace }) => namespace !== 'shell')
    .map(({ locale, namespace, messages }) => ({ locale, namespace, messages })),
  screens: composition.routes.map(({ route }) => route.component?.name ?? null),
}
`
}

function assertExampleContributions(actual) {
  assertJson(actual.extensionIds, ['magrathea-example'], 'extension registration')
  assertJson(actual.navigation, [{
    labelKey: 'example.navigation.status',
    route: '/example/status',
    permission: 'example:status:read',
  }], 'navigation')
  assertJson(actual.routes, ['/example/status'], 'route')
  assertJson(actual.permissions, ['example:status:read'], 'permission')
  assertJson(actual.localization, [{
    locale: 'en',
    namespace: 'example',
    messages: { navigation: { status: 'Example' } },
  }], 'localization')
  assertJson(actual.screens, ['ExampleStatusScreen'], 'screen')
}

function assertNoExampleContributions(actual) {
  assertJson(actual.extensionIds, [], 'removed extension registration')
  assertJson(actual.navigation, [], 'removed navigation')
  assertJson(actual.routes, [], 'removed route')
  assertJson(actual.permissions, [], 'removed permission')
  assertJson(actual.localization, [], 'removed localization')
  assertJson(actual.screens, [], 'removed screen')
}

function run(args) {
  const result = spawnSync('npm', args, { cwd: workspace, encoding: 'utf8', stdio: 'pipe' })
  if (result.status !== 0) throw new Error([result.stdout, result.stderr].filter(Boolean).join('\n'))
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

function assertJson(actual, expected, label) {
  const actualJson = JSON.stringify(actual)
  const expectedJson = JSON.stringify(expected)
  assert(actualJson === expectedJson, `Unexpected ${label}: ${actualJson}; expected ${expectedJson}`)
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
