<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ProductShell, createExtensionComposer, defaultProductIdentity } from '@magrathea/product-shell'
import { createObjectStorageExtension, objectStorageEnglishMessages } from '@magrathea/object-storage-extension'
import { shellLabels, supportedLocales } from './app-localization.js'
import { adminDocumentTitleForRoute } from './browser-adapters'

const props = defineProps({
  initialLocale: { type: String, default: 'en' },
  localePreference: { type: Object, default: undefined },
  extensionComposer: { type: Object, default: undefined },
  onLocaleApplied: { type: Function, default: undefined },
  onPageTitleChange: { type: Function, default: undefined },
  onEnglishFallback: { type: Function, default: undefined },
})
const route = useRoute()
const extension = createObjectStorageExtension()
const composer = props.extensionComposer || createExtensionComposer([extension])
const composition = ref(composer.snapshot())
const selectedLocale = ref(supportedLocales.some(({ locale }) => locale === props.initialLocale) ? props.initialLocale : 'en')
const navigation = computed(() => [
  ...(composition.value.extensionLoadStates.length === 0 ? (extension.navigation || []) : composition.value.navigation),
  { id: 'documentation', route: '/admin/docs', labelKey: 'objectStorage.nav.documentation', order: 100 },
])
const labels = objectStorageEnglishMessages.nav
const descriptions = {
  dashboard: 'Liveness, readiness, and component status from the Admin Control Plane.',
  backend: 'Selected backend, catalog sources, and storage-root availability.',
  policies: 'Read-only storage configuration and non-persistent validation.',
  devices: 'Read-only device health, capacity, and eligibility.',
  'disk-sets': 'Read-only failure-domain topology.', capacity: 'Bucket capacity accounting without object access.',
  'data-hygiene': 'Provider-backed recovery, garbage-collection, and scrub reports.',
  observability: 'Provider-backed audit, metrics, and traces.',
  diagnostics: 'Signed HeadObject through a separately configured S3 Data Plane client.',
  documentation: 'Generated product, architecture, test, and API documentation.',
}
const kind = computed(() => String(route.meta.kind || 'dashboard'))
const pageTitle = computed(() => ({
  dashboard: 'Admin dashboard', backend: 'Backend status', policies: 'Storage policies',
  'policy-detail': String(route.params.id || 'Storage policy'), 'policy-validation': 'Validate a policy proposal',
  devices: 'Storage devices', 'device-detail': String(route.params.id || 'Storage device'),
  'disk-sets': 'Disk-set topology', 'disk-set-detail': String(route.params.id || 'Disk set'),
  capacity: 'Bucket capacity lookup', 'capacity-detail': `Capacity: ${String(route.params.bucket || '')}`,
  'data-hygiene': 'Data hygiene', observability: 'Observability', diagnostics: 'S3 HeadObject diagnostic',
  documentation: 'Documentation',
}[kind.value] || 'Object Storage administration'))
const pageDescription = computed(() => descriptions[kind.value] || 'Admin Control Plane evidence.')
const documentPageTitle = computed(() => adminDocumentTitleForRoute(route))
const activeRoute = computed(() => navigation.value.find((item) => route.path === item.route || (item.route !== '/admin' && route.path.startsWith(`${item.route}/`)))?.route)
const breadcrumbs = computed(() => {
  const crumbs = [{ label: 'Dashboard', href: '/admin' }]
  if (route.path === '/admin') return []
  if (kind.value === 'policy-detail') crumbs.push({ label: 'Storage policies', href: '/admin/storage-policies' })
  if (kind.value === 'device-detail') crumbs.push({ label: 'Storage devices', href: '/admin/storage-devices' })
  if (kind.value === 'disk-set-detail') crumbs.push({ label: 'Disk-set topology', href: '/admin/disk-sets' })
  if (kind.value === 'capacity-detail') crumbs.push({ label: 'Bucket capacity', href: '/admin/capacity' })
  crumbs.push({ label: pageTitle.value })
  return crumbs
})
function navigationLabel(key) { return labels[key.split('.').at(-1)] || key }
async function selectLocale(locale) {
  if (!supportedLocales.some((registration) => registration.locale === locale)) return
  selectedLocale.value = locale
  await props.localePreference?.save(locale)
  props.onLocaleApplied?.(supportedLocales.find((registration) => registration.locale === locale))
}
async function retryExtension(extensionId) { composition.value = await composer.retry(extensionId) }

const reportedFallbacks = new Set()
watch([selectedLocale, kind, documentPageTitle], ([locale, pageKind, title]) => {
  props.onPageTitleChange?.(title, locale)
  if (locale !== 'en') {
    const key = `pages.${pageKind}.title`
    if (!reportedFallbacks.has(`${locale}:${key}`)) {
      reportedFallbacks.add(`${locale}:${key}`)
      props.onEnglishFallback?.({ locale, fallbackLocale: 'en', namespace: 'objectStorage', key, outcome: 'fallback' })
    }
  }
}, { immediate: true })
onMounted(async () => { composition.value = await composer.start() })
</script>

<template>
  <ProductShell
    :identity="{ ...defaultProductIdentity, name: 'Magrathea Object Storage', accessibleName: 'Magrathea Object Storage administration', mark: 'OS' }"
    :navigation="navigation" :active-route="activeRoute" :breadcrumbs="breadcrumbs"
    :page-title="pageTitle" :page-description="pageDescription" :locales="supportedLocales"
    :selected-locale="selectedLocale" :labels="shellLabels(selectedLocale)"
    :extension-load-states="composition.extensionLoadStates"
    @locale-change="selectLocale" @retry-extension="retryExtension">
    <template #navigation-label="{ entry }">{{ navigationLabel(entry.labelKey) }}</template>
    <router-view />
  </ProductShell>
</template>
