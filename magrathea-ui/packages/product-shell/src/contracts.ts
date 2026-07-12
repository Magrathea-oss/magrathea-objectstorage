import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'

export type ExtensionId = string
export type CapabilityId = string
export type FeatureFlagId = string
export type MessageKey = string
export type AppearancePreference = 'system' | 'light' | 'dark'
export type ResolvedAppearance = Exclude<AppearancePreference, 'system'>

/** Semantic identifiers; renderers remain free to choose an icon library or text fallback. */
export type ProductNavigationIconId =
  | 'overview'
  | 'activity'
  | 'layers'
  | 'checklist'
  | 'gauge'
  | 'operations'
  | 'settings'
  | 'documentation'
  | 'information'
  | 'warning'

export type ProductNavigationStatusTone = 'neutral' | 'info' | 'positive' | 'warning' | 'critical' | 'unavailable'

export interface ProductIdentity {
  readonly name: string
  readonly accessibleName: string
  readonly mark?: string
  readonly logoUrl?: string
}

export type BrandTokenSlot =
  | '--shell-brand-strong'
  | '--shell-brand'
  | '--shell-brand-accent'
  | '--shell-brand-soft'

export type BrandTokenOverrides = Readonly<Partial<Record<BrandTokenSlot, string>>>

/** @deprecated Use ProductIdentity. */
export type ProductBrand = Partial<ProductIdentity>

export interface FeatureFlag {
  readonly id: FeatureFlagId
  readonly enabled: boolean
}

export interface CapabilityDeclaration {
  readonly id: CapabilityId
  readonly description?: string
}

export interface ContributionAvailability {
  readonly requiredCapabilities?: readonly CapabilityId[]
  readonly requiredFeatureFlags?: readonly FeatureFlagId[]
}

export interface ProductNavigationStatus {
  readonly labelKey: MessageKey
  readonly tone?: ProductNavigationStatusTone
}

export interface ProductNavigationGroup extends ContributionAvailability {
  readonly id: string
  readonly labelKey: MessageKey
  readonly descriptionKey?: MessageKey
  readonly icon?: ProductNavigationIconId
  readonly order?: number
}

export interface ProductNavigationEntry extends ContributionAvailability {
  readonly id: string
  readonly labelKey: MessageKey
  readonly route: string
  readonly permission?: string
  readonly order?: number
  /** Optional extension-owned task group. Omit to retain legacy flat navigation. */
  readonly groupId?: string
  readonly descriptionKey?: MessageKey
  readonly icon?: ProductNavigationIconId
  readonly status?: ProductNavigationStatus
}

export interface ProductRouteRegistration extends ContributionAvailability {
  readonly id: string
  readonly route: RouteRecordRaw
  readonly order?: number
}

export type LocalizationMessages = Readonly<Record<string, unknown>>

export interface LocalizationBundle {
  readonly locale: string
  readonly namespace: string
  readonly messages: LocalizationMessages
}

export interface ProductExtension {
  readonly id: ExtensionId
  readonly order?: number
  readonly navigation?: readonly ProductNavigationEntry[]
  readonly navigationGroups?: readonly ProductNavigationGroup[]
  readonly routes?: readonly ProductRouteRegistration[]
  readonly permissions?: readonly string[]
  readonly capabilities?: readonly CapabilityDeclaration[]
  readonly localization?: readonly LocalizationBundle[]
  readonly setup?: () => void | Promise<void>
  readonly errorView?: Component
}

export interface ProductShellConfiguration {
  readonly extensions: readonly ProductExtension[]
  readonly identity?: Partial<ProductIdentity>
  readonly brandTokens?: BrandTokenOverrides
  /** @deprecated Use identity. */
  readonly brand?: ProductBrand
  readonly defaultLocale?: string
  readonly defaultAppearance?: AppearancePreference
  readonly featureFlags?: readonly FeatureFlag[]
  readonly capabilities?: readonly CapabilityId[]
}

export interface AppearancePreferencePort {
  /** Loads a public preference value; persistence details remain private to the platform adapter. */
  load(): AppearancePreference | undefined | Promise<AppearancePreference | undefined>
  save(preference: AppearancePreference): void | Promise<void>
}

export interface SystemAppearancePort {
  current(): ResolvedAppearance
  subscribe?(listener: (appearance: ResolvedAppearance) => void): () => void
}

export interface LocalePreferencePort {
  /** Loads a locale value; persistence keys remain private to the platform adapter. */
  load(): string | undefined | Promise<string | undefined>
  /** Saves only the public locale value; the shell never supplies a storage key. */
  save(locale: string): void | Promise<void>
}

export interface LocaleRegistration {
  readonly locale: string
  readonly localizedName: string
  readonly documentLanguage?: string
}

export interface LocaleSelection {
  readonly locale: string
  readonly localizedName: string
  readonly documentLanguage: string
  readonly source: 'saved' | 'default' | 'fallback'
}

export interface LocaleSelectionReporter {
  unsupportedSavedLocale(details: {
    readonly savedLocale: string
    readonly selectedLocale: string
  }): void
}

export interface LocalizationIssueReporter {
  missingMessage(details: {
    readonly locale: string
    readonly fallbackLocale: string
    readonly namespace: string
    readonly outcome: 'fallback' | 'unavailable'
  }): void
}
