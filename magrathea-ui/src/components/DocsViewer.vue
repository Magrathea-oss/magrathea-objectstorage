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

    <!-- JSON-rendered content -->
    <div v-else class="docs-content">
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

const tabs = [
  { id: 'usermanual', label: 'User Manual', url: lang => `/docs/index.${lang}.json` },
  { id: 'arc42', label: 'ARC42', url: () => '/docs/arc42.json' },
  { id: 'testreport', label: 'Test Report', url: () => '/docs/test-report.json' },
]

function getDocUrl(tabId) {
  if (tabId === 'usermanual') {
    const lang = locale.value || 'en'
    return `/docs/index.${lang}.json`
  }
  if (tabId === 'adr') {
    const adrId = route.params.id
    return `/docs/adr/${adrId}.json`
  }
  return tabId === 'arc42' ? '/docs/arc42.json' : '/docs/test-report.json'
}

// Watch locale changes → reload docs
watch(locale, () => {
  if (activeTab.value === 'usermanual') {
    fetchDocs()
  }
})

async function fetchDocs() {
  loading.value = true
  error.value = null
  const tab = activeTab.value

  const cacheKey = tab === 'adr' ? `adr-${route.params.id}` : (tab === 'usermanual' ? (locale.value || 'en') : tab)

  // Return cached if available
  if (docCache.value[cacheKey]) {
    docData.value = docCache.value[cacheKey]
    loading.value = false
    lastUpdated.value = new Date().toLocaleString()
    return
  }

  try {
    const url = getDocUrl(tab)
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    const json = await res.json()
    docData.value = json
    docCache.value[cacheKey] = json
    lastUpdated.value = new Date().toLocaleString()
  } catch (e) {
    error.value = e.message || t('errors.general')
  } finally {
    loading.value = false
  }
}

function switchTab(tabId) {
  activeTab.value = tabId
  fetchDocs()
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
