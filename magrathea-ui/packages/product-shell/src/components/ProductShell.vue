<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type {
  AppearancePreference,
  BrandTokenOverrides,
  LocaleRegistration,
  ProductIdentity,
  ProductNavigationEntry,
  ProductNavigationGroup,
} from '../contracts'
import type { ExtensionLoadState } from '../composition'
import { resolveBrandTokens, resolveProductIdentity } from '../identity'
import { createNavigationSections } from '../presentation'
import type { ProductShellLabels, ShellBreadcrumb } from '../presentation'
import ShellIcon from './ShellIcon.vue'

const defaultLabels: ProductShellLabels = Object.freeze({
  skipToMain: 'Skip to main content',
  primaryNavigation: 'Primary navigation',
  openNavigation: 'Open navigation',
  closeNavigation: 'Close navigation',
  menu: 'Menu',
  explore: 'Explore',
  workspace: 'Workspace',
  locale: 'Language',
  appearance: 'Appearance',
  systemAppearance: 'System',
  lightAppearance: 'Light',
  darkAppearance: 'Dark',
  retry: 'Try again',
  extensionLoadError: (extensionName: string) => `The ${extensionName} extension could not be loaded.`,
})

const props = withDefaults(defineProps<{
  identity?: Partial<ProductIdentity>
  brandTokens?: BrandTokenOverrides
  labels?: Partial<ProductShellLabels>
  navigation?: readonly ProductNavigationEntry[]
  navigationGroups?: readonly ProductNavigationGroup[]
  activeRoute?: string
  breadcrumbs?: readonly ShellBreadcrumb[]
  pageTitle: string
  pageDescription?: string
  locales?: readonly LocaleRegistration[]
  selectedLocale?: string
  appearance?: AppearancePreference
  showAppearanceControl?: boolean
  extensionLoadStates?: readonly ExtensionLoadState[]
}>(), {
  identity: undefined,
  brandTokens: undefined,
  labels: undefined,
  navigation: () => [],
  navigationGroups: () => [],
  activeRoute: undefined,
  breadcrumbs: () => [],
  pageDescription: undefined,
  locales: () => [],
  selectedLocale: 'en',
  appearance: 'system',
  showAppearanceControl: false,
  extensionLoadStates: () => [],
})

const emit = defineEmits<{
  localeChange: [locale: string]
  appearanceChange: [appearance: AppearancePreference]
  retryExtension: [extensionId: string]
}>()

const navigationOpen = ref(false)
const navigationTrigger = ref<HTMLButtonElement>()
const navigationPanel = ref<HTMLElement>()
const logoFailed = ref(false)
const identity = computed(() => resolveProductIdentity({ identity: props.identity }))
const brandStyle = computed(() => resolveBrandTokens(props.brandTokens))
const labels = computed<ProductShellLabels>(() => ({ ...defaultLabels, ...props.labels }))
const failedExtensions = computed(() => props.extensionLoadStates.filter((state) => state.status === 'error'))
const navigationSections = computed(() => props.navigationGroups.length || props.navigation.some((entry) => entry.groupId)
  ? createNavigationSections(props.navigation, props.navigationGroups)
  : [{ entries: props.navigation }])

watch(() => identity.value.logoUrl, () => { logoFailed.value = false })

async function toggleNavigation(): Promise<void> {
  navigationOpen.value = !navigationOpen.value
  if (navigationOpen.value) {
    await nextTick()
    focusableNavigation()[0]?.focus()
  }
}

async function closeNavigationAndRestoreFocus(): Promise<void> {
  if (!navigationOpen.value) return
  navigationOpen.value = false
  await nextTick()
  navigationTrigger.value?.focus()
}

function focusableNavigation(): HTMLElement[] {
  if (!navigationPanel.value) return []
  return Array.from(navigationPanel.value.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'))
}

function onNavigationKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') { event.preventDefault(); void closeNavigationAndRestoreFocus(); return }
  if (event.key !== 'Tab' || !navigationOpen.value) return
  const items = focusableNavigation()
  if (!items.length) { event.preventDefault(); navigationPanel.value?.focus(); return }
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

function selectLocale(event: Event): void {
  emit('localeChange', (event.target as HTMLSelectElement).value)
}

function selectAppearance(event: Event): void {
  emit('appearanceChange', (event.target as HTMLSelectElement).value as AppearancePreference)
}
</script>

<template>
  <div class="product-shell" :style="brandStyle" :data-appearance="appearance">
    <a class="product-shell__skip" href="#main-content">{{ labels.skipToMain }}</a>
    <header class="product-shell__header">
      <a class="product-shell__identity" href="/" :aria-label="`${identity.accessibleName} home`">
        <img v-if="identity.logoUrl && !logoFailed" class="product-shell__logo" :src="identity.logoUrl" alt="" @error="logoFailed = true">
        <span v-else class="product-shell__mark" aria-hidden="true">{{ identity.mark || 'M' }}</span>
        <span class="product-shell__name">{{ identity.name }}</span>
      </a>
      <div class="product-shell__header-actions">
        <slot name="header-actions" />
        <label v-if="showAppearanceControl" class="product-shell__control">
          <span class="shell-sr-only">{{ labels.appearance }}</span>
          <ShellIcon :name="appearance === 'light' ? 'sun' : appearance === 'dark' ? 'moon' : 'system'" />
          <select :value="appearance" :aria-label="labels.appearance" @change="selectAppearance">
            <option value="system">{{ labels.systemAppearance }}</option>
            <option value="light">{{ labels.lightAppearance }}</option>
            <option value="dark">{{ labels.darkAppearance }}</option>
          </select>
        </label>
        <label v-if="locales.length" class="product-shell__control product-shell__locale">
          <span class="shell-sr-only">{{ labels.locale }}</span>
          <select :value="selectedLocale" :aria-label="labels.locale" @change="selectLocale">
            <option v-for="locale in locales" :key="locale.locale" :value="locale.locale" :lang="locale.documentLanguage || locale.locale">
              {{ locale.localizedName }}
            </option>
          </select>
        </label>
        <button ref="navigationTrigger" class="product-shell__menu" type="button" aria-controls="primary-navigation"
          :aria-expanded="navigationOpen" :aria-label="navigationOpen ? labels.closeNavigation : labels.openNavigation" @click="toggleNavigation">
          <ShellIcon :name="navigationOpen ? 'close' : 'menu'" /><span class="product-shell__menu-label">{{ labels.menu }}</span>
        </button>
      </div>
    </header>

    <div v-if="navigationOpen" class="product-shell__backdrop" aria-hidden="true" @click="closeNavigationAndRestoreFocus"></div>

    <div class="product-shell__body">
      <nav id="primary-navigation" ref="navigationPanel" class="product-shell__navigation"
        :class="{ 'product-shell__navigation--open': navigationOpen }" :aria-label="labels.primaryNavigation"
        :tabindex="navigationOpen ? -1 : undefined" @keydown="onNavigationKeydown">
        <div class="product-shell__nav-brandline" aria-hidden="true"></div>
        <div class="product-shell__nav-heading">{{ labels.explore }}</div>
        <div class="product-shell__sections">
          <section v-for="(section, sectionIndex) in navigationSections" :key="section.group?.id || `ungrouped-${sectionIndex}`" class="product-shell__nav-section">
            <header v-if="section.group" class="product-shell__nav-section-heading">
              <ShellIcon v-if="section.group.icon" :name="section.group.icon" />
              <div>
                <h2><slot name="navigation-group-label" :group="section.group">{{ section.group.labelKey }}</slot></h2>
                <p v-if="section.group.descriptionKey"><slot name="navigation-group-description" :group="section.group">{{ section.group.descriptionKey }}</slot></p>
              </div>
            </header>
            <ul>
              <li v-for="entry in section.entries" :key="entry.id">
                <a :href="entry.route" :aria-current="entry.route === activeRoute ? 'page' : undefined" @click="navigationOpen = false">
                  <span class="product-shell__nav-icon" aria-hidden="true"><ShellIcon :name="entry.icon || 'overview'" /></span>
                  <span class="product-shell__nav-copy">
                    <span class="product-shell__nav-label"><slot name="navigation-label" :entry="entry">{{ entry.labelKey }}</slot></span>
                    <span v-if="entry.descriptionKey" class="product-shell__nav-description"><slot name="navigation-description" :entry="entry">{{ entry.descriptionKey }}</slot></span>
                  </span>
                  <span v-if="entry.status" class="product-shell__nav-status" :class="`product-shell__nav-status--${entry.status.tone || 'neutral'}`">
                    <slot name="navigation-status" :status="entry.status" :entry="entry">{{ entry.status.labelKey }}</slot>
                  </span>
                </a>
              </li>
            </ul>
          </section>
        </div>
        <footer v-if="$slots['navigation-footer']" class="product-shell__nav-footer"><slot name="navigation-footer" /></footer>
      </nav>

      <main id="main-content" class="product-shell__main" tabindex="-1">
        <nav v-if="breadcrumbs.length" class="product-shell__breadcrumbs" aria-label="Breadcrumb">
          <ol>
            <li v-for="(item, index) in breadcrumbs" :key="`${item.label}-${index}`">
              <a v-if="item.href && index < breadcrumbs.length - 1" :href="item.href">{{ item.label }}</a>
              <span v-else :aria-current="index === breadcrumbs.length - 1 ? 'page' : undefined">{{ item.label }}</span>
            </li>
          </ol>
        </nav>

        <section v-if="failedExtensions.length" class="product-shell__extension-errors" aria-label="Extension status">
          <div v-for="failure in failedExtensions" :key="failure.extensionId" class="product-shell__extension-error" role="alert">
            <ShellIcon name="error" />
            <p>{{ labels.extensionLoadError(failure.messageParameters.extension) }}</p>
            <button class="shell-button shell-button--secondary" type="button" @click="emit('retryExtension', failure.extensionId)">{{ labels.retry }}</button>
          </div>
        </section>

        <div class="product-shell__page-header">
          <div>
            <p class="product-shell__eyebrow">{{ labels.workspace }}</p>
            <h1>{{ pageTitle }}</h1>
            <p v-if="pageDescription">{{ pageDescription }}</p>
          </div>
          <div v-if="$slots['page-actions']" class="product-shell__page-actions"><slot name="page-actions" /></div>
        </div>
        <slot />
      </main>
    </div>
  </div>
</template>

<style>
.product-shell { width: 100%; min-width: 0; min-height: 100vh; overflow-x: clip; color: var(--shell-text); background-color: var(--shell-surface-canvas); background-image: linear-gradient(rgb(128 103 220 / 3%) 1px, transparent 1px), linear-gradient(90deg, rgb(39 166 154 / 3%) 1px, transparent 1px), radial-gradient(circle at 90% 8%, rgb(128 103 220 / 8%), transparent 24rem); background-size: 32px 32px, 32px 32px, auto; }
.product-shell__skip { position: fixed; z-index: 100; top: var(--shell-space-3); left: var(--shell-space-3); padding: var(--shell-space-3) var(--shell-space-4); color: #fff; background: #553bb2; border-radius: var(--shell-radius-sm); font-weight: 750; transform: translateY(-160%); transition: transform var(--shell-motion-fast); }
.product-shell__skip:focus { transform: translateY(0); }
.product-shell__header { position: sticky; z-index: 30; top: 0; min-height: 3.75rem; display: flex; align-items: center; justify-content: space-between; gap: var(--shell-space-2); padding: var(--shell-space-2) clamp(0.75rem, 2vw, 1.5rem); color: var(--shell-inverse-text); background: #101725; border-bottom: 1px solid #2b3549; box-shadow: 0 8px 24px rgb(0 0 0 / 13%); }
.product-shell__identity { min-width: 0; display: inline-flex; align-items: center; gap: var(--shell-space-3); color: inherit; font-size: var(--shell-text-md); font-weight: 800; letter-spacing: -0.015em; text-decoration: none; }
.product-shell__name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-shell__mark, .product-shell__logo { width: 2rem; height: 2rem; flex: none; display: grid; place-items: center; border: 1px solid rgb(255 255 255 / 18%); border-radius: 0.6rem; color: #fff; background: linear-gradient(145deg, #7357d9, #347f89); font-weight: 850; box-shadow: inset 0 1px rgb(255 255 255 / 22%); }
.product-shell__logo { object-fit: contain; background: #fff; }
.product-shell__header-actions { min-width: 0; display: flex; align-items: center; gap: var(--shell-space-2); }
.product-shell__control { position: relative; min-height: 2.75rem; display: inline-flex; align-items: center; color: var(--shell-inverse-muted); }
.product-shell__control > .shell-icon { position: absolute; left: 0.7rem; z-index: 1; width: 1rem; pointer-events: none; }
.product-shell__control select { width: auto; max-width: 9rem; min-height: 2.75rem; padding: 0.45rem 1.8rem 0.45rem 2.1rem; color: var(--shell-inverse-text); background: #192235; border: 1px solid #3a465d; border-radius: var(--shell-radius-sm); }
.product-shell__locale:not(:has(.shell-icon)) select { padding-left: 0.7rem; }
.product-shell__menu { min-width: 2.75rem; min-height: 2.75rem; display: inline-flex; align-items: center; justify-content: center; gap: var(--shell-space-2); padding: var(--shell-space-2); color: var(--shell-inverse-text); background: transparent; border: 1px solid #46536b; border-radius: var(--shell-radius-sm); cursor: pointer; }
.product-shell__body { width: 100%; min-width: 0; margin-inline: auto; }
.product-shell__backdrop { position: fixed; z-index: 20; inset: 3.75rem 0 0; background: rgb(4 8 16 / 64%); }
.product-shell__navigation { position: fixed; z-index: 25; inset: 3.75rem 0 0 auto; width: min(20rem, 88vw); max-width: 100%; display: flex; flex-direction: column; padding: var(--shell-space-5) var(--shell-space-4); overflow-x: hidden; overflow-y: auto; color: var(--shell-inverse-text); background: #101725; border-left: 1px solid #2b3549; visibility: hidden; transform: translateX(105%); transition: transform var(--shell-motion-normal), visibility 0s linear var(--shell-motion-normal); }
.product-shell__navigation--open { visibility: visible; transform: translateX(0); box-shadow: -1rem 0 3rem rgb(0 0 0 / 32%); transition-delay: 0s; }
.product-shell__nav-brandline { width: 2.5rem; height: 3px; margin-bottom: var(--shell-space-4); background: linear-gradient(90deg, var(--shell-accent-violet), var(--shell-accent-teal), var(--shell-accent-orange)); border-radius: 999px; }
.product-shell__nav-heading { margin-bottom: var(--shell-space-4); color: #8995a8; font-size: 0.68rem; font-weight: 800; letter-spacing: 0.15em; text-transform: uppercase; }
.product-shell__sections { display: grid; gap: var(--shell-space-5); }
.product-shell__nav-section { min-width: 0; }
.product-shell__nav-section-heading { display: flex; align-items: start; gap: var(--shell-space-2); padding: 0 var(--shell-space-2) var(--shell-space-2); color: #98a5b8; }
.product-shell__nav-section-heading > .shell-icon { width: 1rem; margin-top: 0.12rem; color: #7d8da5; }
.product-shell__nav-section-heading h2 { margin: 0; color: #c4ccda; font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; }
.product-shell__nav-section-heading p { margin: var(--shell-space-1) 0 0; font-size: 0.7rem; line-height: 1.35; }
.product-shell__navigation ul { display: grid; gap: 0.2rem; padding: 0; margin: 0; list-style: none; }
.product-shell__navigation a { min-width: 0; min-height: 2.75rem; display: flex; align-items: center; gap: var(--shell-space-3); padding: 0.58rem 0.7rem; overflow-wrap: anywhere; color: #c8d0dc; border: 1px solid transparent; border-radius: var(--shell-radius-sm); text-decoration: none; }
.product-shell__navigation a:hover { color: #fff; background: #192235; border-color: #2b3850; }
.product-shell__navigation a[aria-current='page'] { color: #fff; background: linear-gradient(90deg, rgb(128 103 220 / 24%), rgb(39 166 154 / 8%)); border-color: rgb(128 103 220 / 36%); box-shadow: inset 3px 0 #8b72e6; }
.product-shell__nav-icon { width: 1.8rem; height: 1.8rem; display: grid; place-items: center; flex: none; color: #95a3b8; background: #192235; border: 1px solid #303c52; border-radius: 0.45rem; }
.product-shell__navigation a[aria-current='page'] .product-shell__nav-icon { color: #c0b4f2; background: #2b2750; border-color: #5d4dac; }
.product-shell__nav-icon .shell-icon { width: 1rem; height: 1rem; }
.product-shell__nav-copy { min-width: 0; display: grid; gap: 0.12rem; flex: 1; }
.product-shell__nav-label { font-size: var(--shell-text-sm); font-weight: 700; }
.product-shell__nav-description { color: #8f9bae; font-size: 0.7rem; line-height: 1.3; }
.product-shell__nav-status { flex: none; color: #b6c0ce; font-size: 0.67rem; font-weight: 800; }
.product-shell__nav-status::before { content: ''; width: 0.42rem; height: 0.42rem; display: inline-block; margin-right: var(--shell-space-1); background: currentColor; border-radius: 50%; }
.product-shell__nav-status--positive { color: #6ed6ab; }.product-shell__nav-status--warning { color: #f0bd70; }.product-shell__nav-status--critical { color: #f39bad; }.product-shell__nav-status--info { color: #83cbed; }.product-shell__nav-status--unavailable { color: #a6afbc; }
.product-shell__nav-footer { margin-top: auto; padding: var(--shell-space-5) var(--shell-space-2) 0; color: #9ca8b9; font-size: var(--shell-text-xs); line-height: 1.5; }
.product-shell__main { width: 100%; max-width: var(--shell-content-max); min-width: 0; margin-inline: auto; padding: clamp(1.25rem, 4vw, 3rem); }
.product-shell__breadcrumbs ol { display: flex; flex-wrap: wrap; gap: var(--shell-space-2); padding: 0; margin: 0 0 var(--shell-space-5); color: var(--shell-text-muted); list-style: none; font-size: var(--shell-text-sm); }
.product-shell__breadcrumbs li + li::before { content: '/'; margin-right: var(--shell-space-2); color: var(--shell-border-strong); }
.product-shell__breadcrumbs a { color: var(--shell-brand); font-weight: 700; }
.product-shell__extension-errors { display: grid; gap: var(--shell-space-3); margin-bottom: var(--shell-space-5); }
.product-shell__extension-error { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: var(--shell-space-3); padding: var(--shell-space-4); color: var(--shell-danger); background: var(--shell-danger-soft); border: 1px solid color-mix(in srgb, var(--shell-danger) 55%, var(--shell-border)); border-radius: var(--shell-radius); }
.product-shell__extension-error p { min-width: 0; margin: 0; overflow-wrap: anywhere; color: var(--shell-text); font-weight: 700; }
.product-shell__page-header { display: flex; flex-wrap: wrap; align-items: end; justify-content: space-between; gap: var(--shell-space-5); margin-bottom: var(--shell-space-6); padding-bottom: var(--shell-space-5); border-bottom: 1px solid var(--shell-border); }
.product-shell__page-header h1 { max-width: 26ch; margin: 0; overflow-wrap: anywhere; font-size: var(--shell-text-xl); font-weight: 800; line-height: 1.08; letter-spacing: -0.035em; }
.product-shell__page-header p:not(.product-shell__eyebrow) { max-width: 68ch; margin: var(--shell-space-3) 0 0; color: var(--shell-text-muted); line-height: 1.6; }
.product-shell__eyebrow { margin: 0 0 var(--shell-space-2); color: var(--shell-brand); font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.13em; text-transform: uppercase; }
.product-shell__page-actions { min-width: 0; display: flex; flex-wrap: wrap; gap: var(--shell-space-2); }
@media (max-width: 30rem) {
  .product-shell__name, .product-shell__menu-label { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
  .product-shell__control select { width: 4.6rem; padding-left: 0.55rem; }
  .product-shell__control > .shell-icon { display: none; }
  .product-shell__extension-error { grid-template-columns: auto minmax(0, 1fr); }
  .product-shell__extension-error .shell-button { grid-column: 1 / -1; width: 100%; }
}
@media (min-width: 48rem) {
  .product-shell__body { display: grid; grid-template-columns: 16rem minmax(0, 1fr); }
  .product-shell__menu, .product-shell__backdrop { display: none; }
  .product-shell__navigation { position: sticky; inset: auto; top: 3.75rem; width: auto; height: calc(100vh - 3.75rem); visibility: visible; transform: none; transition: none; border-left: 0; border-right: 1px solid #2b3549; }
}
@media (min-width: 75rem) { .product-shell__body { grid-template-columns: 18rem minmax(0, 1fr); } }
@media (forced-colors: active) {
  .product-shell { background-image: none; }
  .product-shell__header, .product-shell__navigation, .product-shell__extension-error { border: 1px solid CanvasText; }
  .product-shell__navigation a[aria-current='page'] { outline: 2px solid Highlight; outline-offset: -2px; }
  .product-shell__mark, .product-shell__nav-icon { border: 1px solid ButtonText; }
  .product-shell__nav-status::before { border: 1px solid currentColor; }
}
@media (prefers-reduced-motion: reduce) { .product-shell__skip, .product-shell__navigation { transition: none; } }
</style>
