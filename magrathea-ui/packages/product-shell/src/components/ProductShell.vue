<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { BrandTokenOverrides, LocaleRegistration, ProductIdentity, ProductNavigationEntry } from '../contracts'
import type { ExtensionLoadState } from '../composition'
import { resolveBrandTokens, resolveProductIdentity } from '../identity'
import type { ProductShellLabels, ShellBreadcrumb } from '../presentation'

const defaultLabels: ProductShellLabels = Object.freeze({
  skipToMain: 'Skip to main content',
  primaryNavigation: 'Primary navigation',
  openNavigation: 'Open navigation',
  closeNavigation: 'Close navigation',
  menu: 'Menu',
  explore: 'Explore',
  workspace: 'Workspace',
  locale: 'Language',
  retry: 'Try again',
  extensionLoadError: (extensionName: string) => `The ${extensionName} extension could not be loaded.`,
})

const props = withDefaults(defineProps<{
  identity?: Partial<ProductIdentity>
  brandTokens?: BrandTokenOverrides
  labels?: Partial<ProductShellLabels>
  navigation?: readonly ProductNavigationEntry[]
  activeRoute?: string
  breadcrumbs?: readonly ShellBreadcrumb[]
  pageTitle: string
  pageDescription?: string
  locales?: readonly LocaleRegistration[]
  selectedLocale?: string
  extensionLoadStates?: readonly ExtensionLoadState[]
}>(), {
  identity: undefined,
  brandTokens: undefined,
  labels: undefined,
  navigation: () => [],
  activeRoute: undefined,
  breadcrumbs: () => [],
  pageDescription: undefined,
  locales: () => [],
  selectedLocale: 'en',
  extensionLoadStates: () => [],
})

const emit = defineEmits<{
  localeChange: [locale: string]
  retryExtension: [extensionId: string]
}>()

const navigationOpen = ref(false)
const navigationTrigger = ref<HTMLButtonElement>()
const logoFailed = ref(false)
const identity = computed(() => resolveProductIdentity({ identity: props.identity }))
const brandStyle = computed(() => resolveBrandTokens(props.brandTokens))
const labels = computed<ProductShellLabels>(() => ({ ...defaultLabels, ...props.labels }))
const failedExtensions = computed(() => props.extensionLoadStates.filter((state) => state.status === 'error'))

watch(() => identity.value.logoUrl, () => { logoFailed.value = false })

function toggleNavigation(): void {
  navigationOpen.value = !navigationOpen.value
}

async function closeNavigationAndRestoreFocus(): Promise<void> {
  if (!navigationOpen.value) return
  navigationOpen.value = false
  await nextTick()
  navigationTrigger.value?.focus()
}

function selectLocale(event: Event): void {
  emit('localeChange', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="product-shell" :style="brandStyle">
    <a class="product-shell__skip" href="#main-content">{{ labels.skipToMain }}</a>
    <header class="product-shell__header">
      <a class="product-shell__identity" href="/" :aria-label="`${identity.accessibleName} home`">
        <img v-if="identity.logoUrl && !logoFailed" class="product-shell__logo" :src="identity.logoUrl" alt="" @error="logoFailed = true">
        <span v-else class="product-shell__mark" aria-hidden="true">{{ identity.mark || 'M' }}</span>
        <span class="product-shell__name">{{ identity.name }}</span>
      </a>
      <div class="product-shell__header-actions">
        <slot name="header-actions" />
        <label v-if="locales.length" class="product-shell__locale">
          <span class="shell-sr-only">{{ labels.locale }}</span>
          <select :value="selectedLocale" :aria-label="labels.locale" @change="selectLocale">
            <option v-for="locale in locales" :key="locale.locale" :value="locale.locale" :lang="locale.documentLanguage || locale.locale">
              {{ locale.localizedName }}
            </option>
          </select>
        </label>
        <button ref="navigationTrigger" class="product-shell__menu shell-button shell-button--secondary" type="button"
          aria-controls="primary-navigation" :aria-expanded="navigationOpen"
          :aria-label="navigationOpen ? labels.closeNavigation : labels.openNavigation" @click="toggleNavigation">
          <span aria-hidden="true">☰</span><span class="product-shell__menu-label">{{ labels.menu }}</span>
        </button>
      </div>
    </header>

    <div class="product-shell__body">
      <nav id="primary-navigation" class="product-shell__navigation" :class="{ 'product-shell__navigation--open': navigationOpen }"
        :aria-label="labels.primaryNavigation" @keydown.esc.stop.prevent="closeNavigationAndRestoreFocus">
        <div class="product-shell__nav-heading">{{ labels.explore }}</div>
        <ul>
          <li v-for="entry in props.navigation" :key="entry.id">
            <a :href="entry.route" :aria-current="entry.route === activeRoute ? 'page' : undefined" @click="navigationOpen = false">
              <span class="product-shell__nav-dot" aria-hidden="true"></span>
              <slot name="navigation-label" :entry="entry">{{ entry.labelKey }}</slot>
            </a>
          </li>
        </ul>
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
            <p>{{ labels.extensionLoadError(failure.messageParameters.extension) }}</p>
            <button class="shell-button shell-button--secondary" type="button" @click="emit('retryExtension', failure.extensionId)">
              {{ labels.retry }}
            </button>
          </div>
        </section>

        <div class="product-shell__page-header">
          <div>
            <p class="product-shell__eyebrow">{{ labels.workspace }}</p>
            <h1>{{ pageTitle }}</h1>
            <p v-if="pageDescription">{{ pageDescription }}</p>
          </div>
          <div class="product-shell__page-actions"><slot name="page-actions" /></div>
        </div>
        <slot />
      </main>
    </div>
  </div>
</template>

<style>
.product-shell { width: 100%; min-width: 0; min-height: 100vh; overflow-x: clip; background: var(--shell-surface-canvas); }
.product-shell__skip { position: fixed; z-index: 100; top: var(--shell-space-3); left: var(--shell-space-3); padding: var(--shell-space-3) var(--shell-space-4); color: white; background: var(--shell-brand-strong); border-radius: var(--shell-radius-sm); font-weight: 700; transform: translateY(-160%); transition: transform var(--shell-motion-fast); }
.product-shell__skip:focus { transform: translateY(0); }
.product-shell__header { position: sticky; z-index: 20; top: 0; min-height: 4.5rem; display: flex; align-items: center; justify-content: space-between; gap: var(--shell-space-2); padding: var(--shell-space-3) clamp(0.75rem, 3vw, 2.5rem); color: white; background: var(--shell-brand-strong); border-bottom: 3px solid var(--shell-brand-accent); }
.product-shell__identity { min-width: 0; display: inline-flex; align-items: center; gap: var(--shell-space-3); color: inherit; font-weight: 800; font-size: var(--shell-text-lg); letter-spacing: -0.02em; text-decoration: none; }
.product-shell__name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-shell__mark, .product-shell__logo { width: 2.25rem; height: 2.25rem; flex: none; display: grid; place-items: center; border-radius: 50% 50% 50% 20%; color: var(--shell-brand-strong); background: var(--shell-brand-accent); font-weight: 900; }
.product-shell__logo { object-fit: contain; background: white; }
.product-shell__header-actions { min-width: 0; display: flex; align-items: center; gap: var(--shell-space-2); }
.product-shell__locale select { width: auto; max-width: 8rem; min-height: 2.75rem; padding: 0.45rem 1.75rem 0.45rem 0.55rem; color: var(--shell-text); background: var(--shell-surface); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-sm); }
.product-shell__menu { flex: none; color: white; background: transparent; border-color: rgb(255 255 255 / 55%); }
.product-shell__body { width: 100%; max-width: 100rem; min-width: 0; margin-inline: auto; }
.product-shell__navigation { position: fixed; z-index: 15; inset: 4.5rem 0 0 auto; width: min(20rem, 88vw); max-width: 100%; padding: var(--shell-space-5); overflow-x: hidden; overflow-y: auto; color: white; background: var(--shell-brand-strong); visibility: hidden; transform: translateX(105%); transition: transform var(--shell-motion-normal), visibility 0s linear var(--shell-motion-normal); }
.product-shell__navigation--open { visibility: visible; transform: translateX(0); box-shadow: var(--shell-shadow); transition-delay: 0s; }
.product-shell__nav-heading { margin-bottom: var(--shell-space-3); color: #b9d3d0; font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.12em; text-transform: uppercase; }
.product-shell__navigation ul { display: grid; gap: var(--shell-space-1); padding: 0; margin: 0; list-style: none; }
.product-shell__navigation a { min-width: 0; min-height: 2.75rem; display: flex; align-items: center; gap: var(--shell-space-3); padding: var(--shell-space-3); overflow-wrap: anywhere; color: inherit; border-radius: var(--shell-radius-sm); text-decoration: none; font-weight: 650; }
.product-shell__navigation a:hover, .product-shell__navigation a[aria-current="page"] { background: rgb(255 255 255 / 13%); }
.product-shell__navigation a[aria-current="page"] { box-shadow: inset 3px 0 var(--shell-brand-accent); }
.product-shell__nav-dot { width: 0.55rem; height: 0.55rem; flex: none; border: 2px solid currentColor; border-radius: 50%; }
.product-shell__main { width: 100%; max-width: var(--shell-content-max); min-width: 0; margin-inline: auto; padding: clamp(1rem, 4vw, 3rem); }
.product-shell__breadcrumbs ol { display: flex; flex-wrap: wrap; gap: var(--shell-space-2); padding: 0; margin: 0 0 var(--shell-space-5); color: var(--shell-text-muted); list-style: none; font-size: var(--shell-text-sm); }
.product-shell__breadcrumbs li + li::before { content: '/'; margin-right: var(--shell-space-2); color: var(--shell-border); }
.product-shell__breadcrumbs a { color: var(--shell-brand); font-weight: 700; }
.product-shell__extension-errors { display: grid; gap: var(--shell-space-3); margin-bottom: var(--shell-space-5); }
.product-shell__extension-error { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--shell-space-3); padding: var(--shell-space-4); color: var(--shell-danger); background: var(--shell-danger-soft); border: 1px solid var(--shell-danger); border-radius: var(--shell-radius); }
.product-shell__extension-error p { min-width: 0; margin: 0; overflow-wrap: anywhere; font-weight: 700; }
.product-shell__page-header { display: flex; flex-wrap: wrap; align-items: end; justify-content: space-between; gap: var(--shell-space-4); margin-bottom: var(--shell-space-6); }
.product-shell__page-header h1 { max-width: 24ch; margin: 0; overflow-wrap: anywhere; font-size: var(--shell-text-xl); line-height: 1.08; letter-spacing: -0.035em; }
.product-shell__page-header p:not(.product-shell__eyebrow) { max-width: 65ch; margin: var(--shell-space-3) 0 0; color: var(--shell-text-muted); line-height: 1.6; }
.product-shell__eyebrow { margin: 0 0 var(--shell-space-2); color: var(--shell-brand); font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.13em; text-transform: uppercase; }
.product-shell__page-actions { display: flex; flex-wrap: wrap; gap: var(--shell-space-2); }
@media (max-width: 30rem) {
  .product-shell__name, .product-shell__menu-label { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
  .product-shell__locale select { width: 5.5rem; }
}
@media (min-width: 48rem) {
  .product-shell__body { display: grid; grid-template-columns: 16rem minmax(0, 1fr); }
  .product-shell__menu { display: none; }
  .product-shell__navigation { position: sticky; inset: auto; top: 4.5rem; width: auto; height: calc(100vh - 4.5rem); visibility: visible; transform: none; transition: none; }
}
@media (min-width: 75rem) { .product-shell__body { grid-template-columns: 18rem minmax(0, 1fr); } }
@media (forced-colors: active) {
  .product-shell__header, .product-shell__navigation, .product-shell__extension-error { border: 1px solid CanvasText; }
  .product-shell__navigation a[aria-current="page"] { outline: 2px solid Highlight; outline-offset: -2px; }
  .product-shell__mark { border: 1px solid ButtonText; }
}
@media (prefers-reduced-motion: reduce) { .product-shell__skip, .product-shell__navigation { transition: none; } }
</style>
