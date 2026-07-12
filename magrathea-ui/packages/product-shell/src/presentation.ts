import type {
  AppearancePreference,
  AppearancePreferencePort,
  ProductNavigationEntry,
  ProductNavigationGroup,
  ResolvedAppearance,
} from './contracts'

export interface ShellBreadcrumb {
  readonly label: string
  readonly href?: string
}

/** Product-neutral shell copy. Applications may replace these values with localized text. */
export interface ProductShellLabels {
  readonly skipToMain: string
  readonly primaryNavigation: string
  readonly openNavigation: string
  readonly closeNavigation: string
  readonly menu: string
  readonly explore: string
  readonly workspace: string
  readonly locale: string
  readonly appearance: string
  readonly systemAppearance: string
  readonly lightAppearance: string
  readonly darkAppearance: string
  readonly retry: string
  readonly extensionLoadError: (extensionName: string) => string
}

export interface ShellTableColumn {
  readonly key: string
  readonly label: string
  readonly align?: 'start' | 'center' | 'end'
}

export interface ShellNavigationSection {
  /** Absent for legacy entries that do not opt into task grouping. */
  readonly group?: ProductNavigationGroup
  readonly entries: readonly ProductNavigationEntry[]
}

/** Builds renderer-ready task sections while retaining every legacy flat entry. */
export function createNavigationSections(
  navigation: readonly ProductNavigationEntry[],
  groups: readonly ProductNavigationGroup[] = [],
): readonly ShellNavigationSection[] {
  const orderedEntries = [...navigation].sort(compareOrderedContributions)
  const orderedGroups = [...groups].sort(compareOrderedContributions)
  const knownGroups = new Set(orderedGroups.map((group) => group.id))
  const sections: ShellNavigationSection[] = orderedGroups.flatMap((group) => {
    const entries = orderedEntries.filter((entry) => entry.groupId === group.id)
    return entries.length ? [Object.freeze({ group, entries: Object.freeze(entries) })] : []
  })
  const ungrouped = orderedEntries.filter((entry) => !entry.groupId || !knownGroups.has(entry.groupId))
  if (ungrouped.length) sections.push(Object.freeze({ entries: Object.freeze(ungrouped) }))
  return Object.freeze(sections)
}

export function resolveAppearance(
  preference: AppearancePreference,
  systemAppearance: ResolvedAppearance,
): ResolvedAppearance {
  return preference === 'system' ? systemAppearance : preference
}

export async function loadAppearancePreference(
  port: AppearancePreferencePort | undefined,
  fallback: AppearancePreference = 'system',
): Promise<AppearancePreference> {
  const saved = await port?.load()
  return isAppearancePreference(saved) ? saved : fallback
}

export async function persistAppearancePreference(
  preference: AppearancePreference,
  port: AppearancePreferencePort,
): Promise<AppearancePreference> {
  await port.save(preference)
  return preference
}

function isAppearancePreference(value: unknown): value is AppearancePreference {
  return value === 'system' || value === 'light' || value === 'dark'
}

function compareOrderedContributions(
  left: { readonly order?: number; readonly id: string },
  right: { readonly order?: number; readonly id: string },
): number {
  return (left.order ?? 0) - (right.order ?? 0) || left.id.localeCompare(right.id)
}
