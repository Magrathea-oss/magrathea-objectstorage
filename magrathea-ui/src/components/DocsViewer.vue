<template>
  <div class="docs-viewer">
    <!-- Header -->
    <div class="docs-header">
      <h2 class="docs-title">{{ docData?.title || $t('docs.title') }}</h2>
      <div class="docs-controls">
        <button class="docs-dashboard-btn" @click="router.push('/')">
          ← {{ $t('nav.dashboard') }}
        </button>
        <button class="docs-refresh-btn" @click="refreshDocs">
          {{ $t('actions.refresh') }}
        </button>
      </div>
    </div>

    <!-- Tabs (hidden for single-doc views like ADR) -->
    <div class="docs-tabs" v-if="activeTab !== 'adr'">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="docs-tab"
        :class="{ 'docs-tab--active': activeTab === tab.id }"
        @click="switchTab(tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="docs-loading">
      <span class="loading-spinner"></span>
      <span>{{ $t('docs.loading') }}</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="docs-error">
      <span class="error-icon">⚠</span>
      <span>{{ error }}</span>
      <button class="docs-retry-btn" @click="fetchDocs">{{ $t('actions.retry') }}</button>
    </div>

    <!-- HTML content (iframe for Javadoc) -->
    <div v-else-if="activeTabHtml" class="docs-iframe-wrapper">
      <iframe
        class="docs-iframe"
        :src="activeTabUrl"
        frameborder="0"
        title="Javadoc"
      ></iframe>
      <div class="docs-iframe-controls">
        <a :href="activeTabUrl" target="_blank" class="docs-open-new-tab">
          {{ $t('docs.openInNewTab') }}
        </a>
      </div>
    </div>

    <!-- JSON-rendered content -->
    <div v-else class="docs-content" :class="{ 'docs-javadoc': activeTab === 'apidocs' }" @click="handleDocLinkClick">
      <div class="docs-json" v-if="docData">
        <template v-for="section in (docData?.document?.sections || [])" :key="section.id">
          <h2 :id="section.id" class="docs-section-title">{{ section.title }}</h2>
          <DocBlockRenderer :blocks="section.blocks || []" />
        </template>
      </div>
    </div>

    <!-- Navigation info -->
    <div class="docs-footer">
      <span class="docs-info">{{ $t('docs.generated') }} — {{ lastUpdated }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import DocBlockRenderer from './DocBlockRenderer.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const { locale } = useI18n()

const props = defineProps({
  initialDocType: {
    type: String,
    default: 'usermanual'
  }
})

const loading = ref(true)
const error = ref(null)
const docData = ref(null)
const lastUpdated = ref('')
const docCache = ref({})
const activeTab = ref(props.initialDocType)
const currentDocUrl = ref('')

const activeTabHtml = computed(() => {
  const tab = tabs.find(t => t.id === activeTab.value)
  return tab ? tab.isHtml : false
})

const activeTabUrl = computed(() => {
  const tab = tabs.find(t => t.id === activeTab.value)
  if (!tab) return ''
  const urlFn = tab.url
  return typeof urlFn === 'function' ? urlFn() : urlFn
})

const activeTabLabel = computed(() => {
  const tab = tabs.find(t => t.id === activeTab.value)
  return tab ? tab.label : ''
})

const tabs = [
  { id: 'usermanual', label: 'User Manual', url: lang => `/docs/index.${lang}.json`, isHtml: false },
  { id: 'arc42', label: 'ARC42', url: () => '/docs/arc42.json', isHtml: false },
  { id: 'testreport', label: 'Test Report', url: () => '/docs/test-report.json', isHtml: false },
  { id: 'apidocs', label: 'Javadoc', url: () => '/docs/javadoc-json/index.json', isHtml: false },
  { id: 'clover', label: 'JaCoCo', url: () => '/docs/jacoco-json/dashboard.json', isHtml: false },
  { id: 'cucumber', label: 'Cucumber', url: () => '/docs/cucumber-json/overview-features.json', isHtml: false },
]

function getDocUrl(tabId) {
  // ADR special case: load from route params
  if (tabId === 'adr') {
    const adrId = route.params.id
    return `/docs/adr/${adrId}.json`
  }
  // Find the tab definition and call its url function
  const tab = tabs.find(t => t.id === tabId)
  if (!tab) return '/docs/index.en.json'
  const urlFn = tab.url
  if (tabId === 'usermanual') {
    // User manual needs locale param
    const lang = locale.value || 'en'
    return urlFn(lang)
  }
  return typeof urlFn === 'function' ? urlFn() : urlFn
}

// Watch locale changes → reload docs
watch(locale, () => {
  if (activeTab.value === 'usermanual') {
    fetchDocs()
  }
})

async function fetchDocs() {
  const tabId = activeTab.value
  const tab = tabs.find(t => t.id === tabId)
  if (tab && tab.isHtml) {
    // HTML tab: set iframe src directly, no JSON loading
    loading.value = false
    docData.value = null
    return
  }
  const url = getDocUrl(tabId)
  await loadJson(url)
}

function switchTab(tabId) {
  activeTab.value = tabId
  fetchDocs()
}

function handleDocLinkClick(event) {
  // Intercept clicks on <a> tags inside docs content to load JSON internally
  const link = event.target.closest('a')
  if (!link) return
  const href = link.getAttribute('href')
  if (!href) return

  // Handle ADR links (without .json extension) — navigate via Vue router
  if (href.startsWith('/docs/adr/')) {
    event.preventDefault()
    const adrId = href.replace('/docs/adr/', '').replace(/\/$/, '')
    router.push(`/docs/adr/${adrId}`)
    return
  }

  // Only handle links ending with .json
  if (!href.endsWith('.json')) return
  event.preventDefault()
  // Resolve relative paths against the current document URL (not the tab URL)
  let url = href
  if (!href.startsWith('/docs/') && !href.startsWith('http')) {
    // Relative path: resolve against currentDocUrl using the URL constructor
    // This correctly handles ../foo.json, ../../foo.json, and same-directory links
    if (currentDocUrl.value) {
      // Resolve relative paths (including ../ and ../../) against currentDocUrl
      // currentDocUrl may be a full URL (after first resolution) or a relative path (initial load)
      // Use URL constructor with origin only if currentDocUrl is a path (not a full URL)
      const base = currentDocUrl.value.startsWith('http')
        ? currentDocUrl.value
        : window.location.origin + currentDocUrl.value
      url = new URL(href, base).toString()
    } else {
      // Fallback: prepend the current docs directory
      const baseUrl = getDocUrl(activeTab.value)
      const baseDir = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1)
      url = baseDir + href
    }
  }
  loadJson(url)
}

async function loadJson(url) {
  loading.value = true
  error.value = null
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    const json = await res.json()
    docData.value = json
    lastUpdated.value = new Date().toLocaleString()
    // Track the URL that was actually loaded, for correct relative link resolution
    currentDocUrl.value = url
  } catch (e) {
    error.value = e.message || t('errors.general')
  } finally {
    loading.value = false
  }
}

function refreshDocs() {
  // Clear cache and re-fetch
  const lang = locale.value || 'en'
  delete docCache.value[lang]
  fetchDocs()
}

/**
 * Retrieve a specific section by its id from the loaded doc data.
 * Useful for tooltip content in forms.
 * @param {string} sectionId - The section id (e.g. '_frontend_overview')
 * @returns {object|null} The section object or null
 */
function getDocSection(sectionId) {
  if (!docData.value?.document?.sections) return null
  // Search top-level sections
  const found = docData.value.document.sections.find(s => s.id === sectionId)
  if (found) return found
  // Search nested sections inside blocks
  for (const section of docData.value.document.sections) {
    const sub = findSectionInBlocks(section.blocks, sectionId)
    if (sub) return sub
  }
  return null
}

/**
 * Recursively search for a section with given id inside blocks array.
 */
function findSectionInBlocks(blocks, sectionId) {
  if (!blocks) return null
  for (const block of blocks) {
    if (block.type === 'section') {
      if (block.id === sectionId) return block
      const found = findSectionInBlocks(block.blocks, sectionId)
      if (found) return found
    }
  }
  return null
}

// Expose getDocSection globally for use by form components
if (typeof window !== 'undefined') {
  window.__getDocSection = getDocSection
}

onMounted(fetchDocs)
</script>

<style scoped>
.docs-viewer {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 4rem);
}

.docs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.docs-title {
  font-size: 1.6rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-teal) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.docs-controls {
  display: flex;
  gap: 0.5rem;
}

.docs-dashboard-btn,
.docs-refresh-btn {
  padding: 0.4rem 1rem;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 500;
  transition: all 0.2s;
}

.docs-dashboard-btn {
  background: var(--accent-teal);
  color: #fff;
  border: 1px solid var(--accent-teal);
}

.docs-dashboard-btn:hover {
  background: var(--accent);
  box-shadow: 0 0 16px var(--accent-glow);
}

/* Tabs */
.docs-tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-glass);
  padding-bottom: 0;
}

.docs-tab {
  padding: 0.5rem 1.25rem;
  border: 1px solid var(--border-glass);
  border-bottom: none;
  border-radius: 8px 8px 0 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.docs-tab:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.docs-tab--active {
  background: var(--bg-card);
  color: var(--accent);
  border-color: var(--accent);
  border-bottom-color: var(--bg-card);
}

.docs-refresh-btn {
  background: var(--accent);
  color: #fff;
}

.docs-refresh-btn:hover {
  background: var(--accent-teal);
  box-shadow: 0 0 16px var(--accent-glow);
}

/* Loading */
.docs-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 3rem;
  color: var(--text-secondary);
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--text-muted);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Error */
.docs-error {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1.5rem;
  background: rgba(255, 92, 92, 0.1);
  border: 1px solid rgba(255, 92, 92, 0.25);
  border-radius: 12px;
  color: var(--danger);
}

.error-icon {
  font-size: 1.5rem;
}

.docs-retry-btn {
  margin-left: auto;
  padding: 0.35rem 0.9rem;
  border-radius: 6px;
  font-size: 0.8rem;
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

/* Content */
.docs-content {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border-glass);
  border-radius: 12px;
  overflow: hidden;
  padding: 2rem;
  overflow-y: auto;
  max-height: calc(100vh - 10rem);
}

.docs-json {
  line-height: 1.6;
}

.docs-section-title {
  font-size: 1.4rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  padding-bottom: 0.5rem;
}

.docs-subsection {
  margin-left: 1rem;
}

.docs-subsection-title {
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 1em;
  margin-bottom: 0.5em;
}

.docs-paragraph {
  margin: 0.6em 0;
  color: var(--text-secondary);
}

.docs-table-wrapper {
  margin: 1em 0;
  overflow-x: auto;
}

.docs-table {
  border-collapse: collapse;
  width: 100%;
}

.docs-table th,
.docs-table td {
  border: 1px solid rgba(255,255,255,0.1);
  padding: 0.6rem 1rem;
  text-align: left;
}

.docs-table th {
  background: rgba(255,255,255,0.06);
  font-weight: 600;
  color: var(--text-primary);
}

.docs-table td {
  color: var(--text-secondary);
}

.docs-list {
  padding-left: 1.5em;
  margin: 0.6em 0;
}

.docs-list li {
  margin: 0.3em 0;
  color: var(--text-secondary);
}

/* Iframe for HTML docs (Javadoc) */
.docs-iframe-wrapper {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border-glass);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.docs-iframe {
  width: 100%;
  flex: 1;
  min-height: calc(100vh - 12rem);
  border: none;
  background: #fff;
}

.docs-iframe-controls {
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--border-glass);
  text-align: center;
}

.docs-open-new-tab {
  color: var(--accent);
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 500;
  padding: 0.4rem 1rem;
  border: 1px solid var(--accent);
  border-radius: 8px;
  transition: all 0.2s;
}

.docs-open-new-tab:hover {
  background: var(--accent);
  color: #fff;
}

/* Footer */
.docs-footer {
  margin-top: 1rem;
  padding: 0.75rem 0;
  border-top: 1px solid var(--border-glass);
  display: flex;
  justify-content: flex-end;
}

.docs-info {
  font-size: 0.8rem;
  color: var(--text-muted);
}
</style>
