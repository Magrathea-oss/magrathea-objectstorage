import type {
  CapabilityDeclaration,
  CapabilityId,
  FeatureFlag,
  LocalizationBundle,
  ProductExtension,
  ProductNavigationEntry,
  ProductRouteRegistration,
} from './contracts'
import { shellEnglishBundle } from './localization'

export type ExtensionFailureStage = 'contract' | 'setup'

export interface ExtensionCompositionFailure {
  readonly extensionId: string
  readonly stage: ExtensionFailureStage
  readonly message: string
}

export type ExtensionLoadState =
  | { readonly extensionId: string; readonly status: 'ready' }
  | {
      readonly extensionId: string
      readonly status: 'error'
      readonly messageKey: 'shell.extensions.loadError'
      readonly messageParameters: Readonly<{ extension: string }>
      readonly recoveryAction: 'retry'
    }

export interface ComposedExtension {
  readonly id: string
  readonly order: number
  readonly navigation: readonly ProductNavigationEntry[]
  readonly routes: readonly ProductRouteRegistration[]
  readonly permissions: readonly string[]
  readonly capabilities: readonly CapabilityDeclaration[]
  readonly localization: readonly LocalizationBundle[]
}

export interface ProductComposition {
  readonly extensions: readonly ComposedExtension[]
  readonly navigation: readonly ProductNavigationEntry[]
  readonly routes: readonly ProductRouteRegistration[]
  readonly permissions: readonly string[]
  readonly capabilities: readonly CapabilityDeclaration[]
  readonly localization: readonly LocalizationBundle[]
  readonly failures: readonly ExtensionCompositionFailure[]
  readonly extensionLoadStates: readonly ExtensionLoadState[]
}

export interface ExtensionComposer {
  start(): Promise<ProductComposition>
  retry(extensionId: string): Promise<ProductComposition>
  snapshot(): ProductComposition
}

export function createExtensionComposer(
  extensions: readonly ProductExtension[],
  options: {
    readonly featureFlags?: readonly FeatureFlag[]
    readonly capabilities?: readonly CapabilityId[]
  } = {},
): ExtensionComposer {
  const definitions = new Map<string, ProductExtension>()
  for (const extension of extensions) {
    assertIdentifier(extension.id, 'extension id')
    if (definitions.has(extension.id)) throw new Error(`Duplicate extension id: ${extension.id}`)
    definitions.set(extension.id, extension)
  }
  const ordered = [...definitions.values()].sort(compareExtensions)
  const healthy = new Map<string, ComposedExtension>()
  const failures = new Map<string, ExtensionCompositionFailure>()
  const enabledFlags = new Set(options.featureFlags?.filter((flag) => flag.enabled).map((flag) => flag.id))
  const capabilities = new Set(options.capabilities ?? [])

  async function attempt(extension: ProductExtension): Promise<void> {
    try {
      const candidate = readExtension(extension, enabledFlags, capabilities)
      assertNoConflicts(candidate, [...healthy.values()])
      try {
        await extension.setup?.()
      } catch (error) {
        failures.set(extension.id, failure(extension.id, 'setup', error))
        return
      }
      healthy.set(extension.id, candidate)
      failures.delete(extension.id)
    } catch (error) {
      failures.set(extension.id, failure(extension.id, 'contract', error))
    }
  }

  return {
    async start() {
      for (const extension of ordered) {
        if (!healthy.has(extension.id)) await attempt(extension)
      }
      return createSnapshot(healthy, failures)
    },
    async retry(extensionId) {
      const extension = definitions.get(extensionId)
      if (!extension) throw new Error(`Unknown extension id: ${extensionId}`)
      if (healthy.has(extensionId)) return createSnapshot(healthy, failures)
      await attempt(extension)
      return createSnapshot(healthy, failures)
    },
    snapshot: () => createSnapshot(healthy, failures),
  }
}

function readExtension(
  extension: ProductExtension,
  enabledFlags: ReadonlySet<string>,
  availableCapabilities: ReadonlySet<string>,
): ComposedExtension {
  const navigation = [...(extension.navigation ?? [])]
    .filter((entry) => isAvailable(entry, enabledFlags, availableCapabilities))
    .sort(compareContributions)
  const routes = [...(extension.routes ?? [])]
    .filter((entry) => isAvailable(entry, enabledFlags, availableCapabilities))
    .sort(compareContributions)
  const permissions = [...new Set(extension.permissions ?? [])].sort()
  const declaredCapabilities = [...(extension.capabilities ?? [])].sort((a, b) => a.id.localeCompare(b.id))
  const localization = [...(extension.localization ?? [])].sort(
    (a, b) => a.locale.localeCompare(b.locale) || a.namespace.localeCompare(b.namespace),
  )

  assertUnique(navigation.map((entry) => entry.id), 'navigation id')
  assertUnique(routes.map((entry) => entry.id), 'route id')
  assertUnique(declaredCapabilities.map((entry) => entry.id), 'capability id')
  assertUnique(localization.map((entry) => `${entry.locale}:${entry.namespace}`), 'localization bundle')
  for (const entry of navigation) {
    assertIdentifier(entry.id, 'navigation id')
    if (!entry.route.startsWith('/')) throw new Error(`Navigation route must be absolute: ${entry.route}`)
  }
  for (const entry of routes) {
    assertIdentifier(entry.id, 'route id')
    if (typeof entry.route.path !== 'string' || !entry.route.path.startsWith('/')) {
      throw new Error(`Route path must be absolute: ${entry.id}`)
    }
  }
  for (const bundle of localization) {
    if (bundle.namespace === 'shell') throw new Error('Extensions cannot contribute to the shell namespace')
  }

  return Object.freeze({
    id: extension.id,
    order: extension.order ?? 0,
    navigation: Object.freeze(navigation),
    routes: Object.freeze(routes),
    permissions: Object.freeze(permissions),
    capabilities: Object.freeze(declaredCapabilities),
    localization: Object.freeze(localization),
  })
}

function assertNoConflicts(candidate: ComposedExtension, healthy: readonly ComposedExtension[]): void {
  const navigationIds = new Set(healthy.flatMap((item) => item.navigation.map((entry) => entry.id)))
  const routeIds = new Set(healthy.flatMap((item) => item.routes.map((entry) => entry.id)))
  const routePaths = new Set(healthy.flatMap((item) => item.routes.map((entry) => entry.route.path)))
  const routeNames = new Set(healthy.flatMap((item) => item.routes.map((entry) => entry.route.name)).filter(Boolean))
  const localization = new Set(
    healthy.flatMap((item) => item.localization.map((bundle) => `${bundle.locale}:${bundle.namespace}`)),
  )
  for (const entry of candidate.navigation) {
    if (navigationIds.has(entry.id)) throw new Error(`Duplicate navigation id: ${entry.id}`)
  }
  for (const entry of candidate.routes) {
    if (routeIds.has(entry.id)) throw new Error(`Duplicate route id: ${entry.id}`)
    if (routePaths.has(entry.route.path)) throw new Error(`Duplicate route path: ${entry.route.path}`)
    if (entry.route.name && routeNames.has(entry.route.name)) throw new Error(`Duplicate route name: ${String(entry.route.name)}`)
  }
  for (const bundle of candidate.localization) {
    const id = `${bundle.locale}:${bundle.namespace}`
    if (localization.has(id)) throw new Error(`Duplicate localization bundle: ${id}`)
  }
}

function createSnapshot(
  healthy: ReadonlyMap<string, ComposedExtension>,
  failed: ReadonlyMap<string, ExtensionCompositionFailure>,
): ProductComposition {
  const extensions = [...healthy.values()].sort((a, b) => a.order - b.order || a.id.localeCompare(b.id))
  const failures = [...failed.values()].sort((a, b) => a.extensionId.localeCompare(b.extensionId))
  const extensionLoadStates: ExtensionLoadState[] = [
    ...extensions.map((extension): ExtensionLoadState => Object.freeze({ extensionId: extension.id, status: 'ready' })),
    ...failures.map((item): ExtensionLoadState => Object.freeze({
      extensionId: item.extensionId,
      status: 'error',
      messageKey: 'shell.extensions.loadError',
      messageParameters: Object.freeze({ extension: item.extensionId }),
      recoveryAction: 'retry',
    })),
  ].sort((a, b) => a.extensionId.localeCompare(b.extensionId))
  return Object.freeze({
    extensions: Object.freeze(extensions),
    navigation: Object.freeze(extensions.flatMap((extension) => extension.navigation).sort(compareContributions)),
    routes: Object.freeze(extensions.flatMap((extension) => extension.routes).sort(compareContributions)),
    permissions: Object.freeze([...new Set(extensions.flatMap((extension) => extension.permissions))].sort()),
    capabilities: Object.freeze(extensions.flatMap((extension) => extension.capabilities)),
    localization: Object.freeze([shellEnglishBundle, ...extensions.flatMap((extension) => extension.localization)]),
    failures: Object.freeze(failures),
    extensionLoadStates: Object.freeze(extensionLoadStates),
  })
}

function isAvailable(
  contribution: { readonly requiredCapabilities?: readonly string[]; readonly requiredFeatureFlags?: readonly string[] },
  flags: ReadonlySet<string>,
  capabilities: ReadonlySet<string>,
): boolean {
  return (
    (contribution.requiredFeatureFlags?.every((flag) => flags.has(flag)) ?? true) &&
    (contribution.requiredCapabilities?.every((capability) => capabilities.has(capability)) ?? true)
  )
}

function compareExtensions(left: ProductExtension, right: ProductExtension): number {
  return (left.order ?? 0) - (right.order ?? 0) || left.id.localeCompare(right.id)
}

function compareContributions(
  left: { readonly order?: number; readonly id: string },
  right: { readonly order?: number; readonly id: string },
): number {
  return (left.order ?? 0) - (right.order ?? 0) || left.id.localeCompare(right.id)
}

function assertIdentifier(value: string, label: string): void {
  if (!/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(value)) throw new Error(`Invalid ${label}: ${value}`)
}

function assertUnique(values: readonly string[], label: string): void {
  const seen = new Set<string>()
  for (const value of values) {
    if (seen.has(value)) throw new Error(`Duplicate ${label}: ${value}`)
    seen.add(value)
  }
}

function failure(extensionId: string, stage: ExtensionFailureStage, error: unknown): ExtensionCompositionFailure {
  return Object.freeze({
    extensionId,
    stage,
    message: error instanceof Error ? error.message : 'Unknown extension composition failure',
  })
}
