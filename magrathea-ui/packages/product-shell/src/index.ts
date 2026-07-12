import './theme.css'

export type {
  BrandTokenOverrides,
  BrandTokenSlot,
  CapabilityDeclaration,
  CapabilityId,
  ContributionAvailability,
  ExtensionId,
  FeatureFlag,
  FeatureFlagId,
  LocalePreferencePort,
  LocaleRegistration,
  LocaleSelection,
  LocaleSelectionReporter,
  LocalizationBundle,
  LocalizationIssueReporter,
  LocalizationMessages,
  MessageKey,
  ProductBrand,
  ProductExtension,
  ProductIdentity,
  ProductNavigationEntry,
  ProductRouteRegistration,
  ProductShellConfiguration,
} from './contracts'
export type {
  ApplicationState,
  ApplicationStateStore,
  PageStatePresentation,
  RecoveryAction,
  ResourceState,
  StandardPageState,
} from './application-state'
export { createApplicationStateStore, standardPagePresentations, withProductComposition } from './application-state'
export type {
  ComposedExtension,
  ExtensionComposer,
  ExtensionCompositionFailure,
  ExtensionFailureStage,
  ExtensionLoadState,
  ProductComposition,
} from './composition'
export { createExtensionComposer } from './composition'
export {
  brandTokenSlots,
  defaultBrandTokens,
  defaultProductIdentity,
  resolveBrandTokens,
  resolveProductIdentity,
} from './identity'
export { default as ProductShell } from './components/ProductShell.vue'
export type { ProductShellLabels, ShellBreadcrumb, ShellTableColumn } from './presentation'
export { default as ShellBadge } from './components/ShellBadge.vue'
export { default as ShellBanner } from './components/ShellBanner.vue'
export { default as ShellCard } from './components/ShellCard.vue'
export { default as ShellDataTable } from './components/ShellDataTable.vue'
export { default as ShellDialog } from './components/ShellDialog.vue'
export { default as ShellField } from './components/ShellField.vue'
export { default as ShellPageState } from './components/ShellPageState.vue'
export { default as ShellSkeleton } from './components/ShellSkeleton.vue'
export type { LocalizationCatalog } from './localization'
export {
  createLocalizationCatalog,
  defaultLocaleRegistrations,
  persistLocale,
  resolveInitialLocale,
  selectInitialLocale,
  shellEnglishBundle,
} from './localization'

export const productShellArtifact = Object.freeze({
  name: '@magrathea/product-shell',
  version: '0.1.0',
})
