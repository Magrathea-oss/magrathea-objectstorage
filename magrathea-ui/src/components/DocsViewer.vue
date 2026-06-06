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
        <template v-for="section in docData.sections" :key="section.id">
          <h2 :id="section.id" class="docs-section-title">{{ section.title }}</h2>

          <!-- Paragraphs -->
          <p v-for="(para, pi) in section.paragraphs" :key="pi" class="docs-paragraph">{{ para }}</p>

          <!-- Tables -->
          <div v-for="(table, ti) in section.tables" :key="'t'+ti" class="docs-table-wrapper">
            <table class="docs-table">
              <thead v-if="table.headers">
                <tr>
                  <th v-for="(h, hi) in table.headers" :key="'h'+hi">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in table.rows" :key="'r'+ri">
                  <td v-for="(cell, ci) in row" :key="'c'+ci">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Subsections -->
          <div v-for="sub in section.subsections" :key="sub.id" class="docs-subsection">
            <h3 :id="sub.id" class="docs-subsection-title">{{ sub.title }}</h3>
            <p v-for="(para, pi) in sub.paragraphs" :key="'p'+pi" class="docs-paragraph">{{ para }}</p>
            <div v-for="(table, ti) in sub.tables" :key="'t'+ti" class="docs-table-wrapper">
              <table class="docs-table">
                <thead v-if="table.headers">
                  <tr>
                    <th v-for="(h, hi) in table.headers" :key="'h'+hi">{{ h }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, ri) in table.rows" :key="'r'+ri">
                    <td v-for="(cell, ci) in row" :key="'c'+ci">{{ cell }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <ul v-if="sub.list" class="docs-list">
              <li v-for="(item, li) in sub.list" :key="'l'+li">{{ item }}</li>
            </ul>
          </div>

          <!-- Inline list at section level -->
          <ul v-if="section.list" class="docs-list">
            <li v-for="(item, li) in section.list" :key="'l'+li">{{ item }}</li>
          </ul>
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
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

const { locale } = useI18n()

const loading = ref(true)
const error = ref(null)
const docData = ref(null)
const lastUpdated = ref('')
const docCache = ref({})

// Watch locale changes → reload docs
watch(locale, () => {
  fetchDocs()
})

async function fetchDocs() {
  loading.value = true
  error.value = null
  const lang = locale.value || 'en'

  // Return cached if available
  if (docCache.value[lang]) {
    docData.value = docCache.value[lang]
    loading.value = false
    lastUpdated.value = new Date().toLocaleString()
    return
  }

  try {
    const url = `/docs/index.${lang}.json`
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    const json = await res.json()
    docData.value = json
    docCache.value[lang] = json
    lastUpdated.value = new Date().toLocaleString()
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
  if (!docData.value?.sections) return null
  // Search top-level sections
  const found = docData.value.sections.find(s => s.id === sectionId)
  if (found) return found
  // Search subsections
  for (const section of docData.value.sections) {
    if (section.subsections) {
      const sub = section.subsections.find(s => s.id === sectionId)
      if (sub) return sub
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
