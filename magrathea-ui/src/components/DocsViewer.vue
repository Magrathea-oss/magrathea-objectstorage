<template>
  <div class="docs-viewer">
    <!-- Header -->
    <div class="docs-header">
      <h2 class="docs-title">{{ $t('docs.title') }}</h2>
      <div class="docs-controls">
        <button class="docs-nav-btn" @click="navigateBack" :disabled="!canGoBack">
          ← {{ $t('docs.back') }}
        </button>
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

    <!-- Content -->
    <div v-else class="docs-content">
      <!-- Iframe for full HTML docs -->
      <iframe
        v-if="useIframe"
        ref="docIframe"
        :src="docUrl"
        class="docs-iframe"
        title="Documentation"
        @load="onIframeLoad"
      />

      <!-- Sanitized HTML view -->
      <div
        v-else
        class="docs-html"
        v-html="sanitizedHtml"
      />
    </div>

    <!-- Navigation info -->
    <div class="docs-footer">
      <span class="docs-info">{{ $t('docs.generated') }} — {{ lastUpdated }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

const loading = ref(true)
const error = ref(null)
const htmlContent = ref('')
const docUrl = ref('/docs/index.html')
const useIframe = ref(true)
const canGoBack = ref(false)
const lastUpdated = ref('')

async function fetchDocs() {
  loading.value = true
  error.value = null
  try {
    const res = await fetch('/docs/index.html')
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    const html = await res.text()
    htmlContent.value = html
    lastUpdated.value = new Date().toLocaleString()
  } catch (e) {
    error.value = e.message || t('errors.general')
  } finally {
    loading.value = false
  }
}

function refreshDocs() {
  // Force re-fetch by clearing cache via a timestamp parameter
  docUrl.value = `/docs/index.html?t=${Date.now()}`
  fetchDocs()
}

function navigateBack() {
  // For iframe mode, we can't easily navigate back inside the iframe
  // This would be handled differently in production
  if (useIframe.value && canGoBack.value) {
    // Try to go back in iframe history
    try {
      const iframe = docIframe.value
      if (iframe?.contentWindow?.history?.back) {
        iframe.contentWindow.history.back()
      }
    } catch (e) {
      // Cross-origin restrictions may prevent this
    }
  }
}

function onIframeLoad() {
  canGoBack.value = true
}

// Basic HTML sanitization for v-html mode
const sanitizedHtml = computed(() => {
  if (!htmlContent.value) return ''
  // Remove script tags and event handlers for security
  let cleaned = htmlContent.value.replace(/<script[^>]*>.*?<\/script>/gi, '')
  cleaned = cleaned.replace(/on\w+="[^"]*"/gi, '')
  cleaned = cleaned.replace(/on\w+='[^']*'/gi, '')
  return cleaned
})

let docIframe = ref(null)

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

.docs-nav-btn,
.docs-dashboard-btn,
.docs-refresh-btn {
  padding: 0.4rem 1rem;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 500;
  transition: all 0.2s;
}

.docs-nav-btn {
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border-glass);
}

.docs-nav-btn:hover:not(:disabled) {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.docs-nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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
}

.docs-iframe {
  width: 100%;
  height: 100%;
  border: none;
  min-height: 500px;
}

.docs-html {
  padding: 2rem;
  overflow-y: auto;
  max-height: calc(100vh - 10rem);
  line-height: 1.6;
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
