<template>
  <div class="storage-policies">
    <!-- Header -->
    <div class="sp-header">
      <h2 class="sp-title">{{ $t('storagePolicies.title') }}</h2>
      <div class="sp-header-actions">
        <button class="sp-dashboard-btn" @click="router.push('/')">
          ← {{ $t('nav.dashboard') }}
        </button>
        <button class="sp-create-btn" @click="openCreateForm">
          {{ $t('storagePolicies.create') }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="sp-loading">
      <span class="loading-spinner"></span>
      <span>{{ $t('storagePolicies.loading') }}</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="sp-error">
      <span class="error-icon">⚠</span>
      <span>{{ error }}</span>
      <button class="sp-retry-btn" @click="fetchPolicies">{{ $t('actions.retry') }}</button>
    </div>

    <!-- Table -->
    <div v-else class="sp-table-container">
      <table class="sp-table">
        <thead>
          <tr>
            <th>{{ $t('storagePolicies.storageClassId') }}</th>
            <th>{{ $t('storagePolicies.dedup') }}</th>
            <th>{{ $t('storagePolicies.compression') }}</th>
            <th>{{ $t('storagePolicies.encryption') }}</th>
            <th>{{ $t('storagePolicies.erasureCoding') }}</th>
            <th>{{ $t('storagePolicies.replication') }}</th>
            <th class="th-actions">{{ $t('actions.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="policy in policies" :key="policy.storageClassId">
            <td class="cell-id">{{ policy.storageClassId }}</td>
            <td class="cell-nested">
              <span v-if="policy.dedup">
                {{ $t('storagePolicies.dedupAlgorithm') }}: {{ policy.dedup.algorithm }}<br>
                {{ $t('storagePolicies.dedupScope') }}: {{ policy.dedup.scope }}<br>
                {{ $t('storagePolicies.dedupChunkSize') }}: {{ policy.dedup.chunkSize }}<br>
                {{ $t('storagePolicies.dedupAlignment') }}: {{ policy.dedup.alignment }}
              </span>
              <span v-else class="cell-na">—</span>
            </td>
            <td class="cell-nested">
              <span v-if="policy.compression">
                {{ $t('storagePolicies.compressionAlgorithm') }}: {{ policy.compression.algorithm }}<br>
                {{ $t('storagePolicies.compressionLevel') }}: {{ policy.compression.level }}
              </span>
              <span v-else class="cell-na">—</span>
            </td>
            <td class="cell-nested">
              <span v-if="policy.encryption">
                {{ $t('storagePolicies.encryptionAlgorithm') }}: {{ policy.encryption.algorithm }}
              </span>
              <span v-else class="cell-na">—</span>
            </td>
            <td class="cell-nested">
              <span v-if="policy.erasureCoding">
                {{ $t('storagePolicies.dataBlocks') }}: {{ policy.erasureCoding.dataBlocks }}<br>
                {{ $t('storagePolicies.parityBlocks') }}: {{ policy.erasureCoding.parityBlocks }}
              </span>
              <span v-else class="cell-na">—</span>
            </td>
            <td class="cell-nested">
              <span v-if="policy.replication">
                {{ $t('storagePolicies.replicationFactor') }}: {{ policy.replication.factor }}
              </span>
              <span v-else class="cell-na">—</span>
            </td>
            <td class="cell-actions">
              <button class="action-btn edit" @click="openEditForm(policy)">
                {{ $t('actions.edit') }}
              </button>
              <button class="action-btn delete" @click="confirmDelete(policy)">
                {{ $t('actions.delete') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Empty state -->
      <div v-if="policies.length === 0" class="sp-empty">
        {{ $t('storagePolicies.empty') }}
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-card">
        <div class="modal-header">
          <h3>{{ isEditing ? $t('storagePolicies.editPolicy') : $t('storagePolicies.createPolicy') }}</h3>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>
        <form class="modal-form" @submit.prevent="savePolicy">
          <div class="form-group">
            <label>{{ $t('storagePolicies.storageClassId') }}</label>
            <input v-model="form.storageClassId" class="form-input" required />
          </div>

          <fieldset>
            <legend>{{ $t('storagePolicies.dedup') }}</legend>
            <div class="form-row">
              <div class="form-group">
                <label>{{ $t('storagePolicies.dedupAlgorithm') }}</label>
                <select v-model="form.dedup.algorithm" class="form-input">
                  <option value="SHA256">SHA256</option>
                  <option value="BLAKE2">BLAKE2</option>
                  <option value="XXHASH">XXHash</option>
                </select>
              </div>
              <div class="form-group">
                <label>{{ $t('storagePolicies.dedupScope') }}</label>
                <select v-model="form.dedup.scope" class="form-input">
                  <option value="OBJECT">Object</option>
                  <option value="BUCKET">Bucket</option>
                  <option value="GLOBAL">Global</option>
                </select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>{{ $t('storagePolicies.dedupChunkSize') }}</label>
                <input v-model="form.dedup.chunkSize" class="form-input" type="number" min="1" />
              </div>
              <div class="form-group">
                <label>{{ $t('storagePolicies.dedupAlignment') }}</label>
                <select v-model="form.dedup.alignment" class="form-input">
                  <option value="NONE">None</option>
                  <option value="BYTE">Byte</option>
                  <option value="BLOCK">Block</option>
                  <option value="STRIPE">Stripe</option>
                </select>
              </div>
            </div>
          </fieldset>

          <fieldset>
            <legend>{{ $t('storagePolicies.compression') }}</legend>
            <div class="form-row">
              <div class="form-group">
                <label>{{ $t('storagePolicies.compressionAlgorithm') }}</label>
                <select v-model="form.compression.algorithm" class="form-input">
                  <option value="GZIP">GZIP</option>
                  <option value="SNAPPY">Snappy</option>
                  <option value="ZSTD">Zstd</option>
                  <option value="LZ4">LZ4</option>
                  <option value="BROTLI">Brotli</option>
                </select>
              </div>
              <div class="form-group">
                <label>{{ $t('storagePolicies.compressionLevel') }}</label>
                <input v-model="form.compression.level" class="form-input" type="number" min="0" max="22" />
              </div>
            </div>
          </fieldset>

          <fieldset>
            <legend>{{ $t('storagePolicies.encryption') }}</legend>
            <div class="form-group">
              <label>{{ $t('storagePolicies.encryptionAlgorithm') }}</label>
              <select v-model="form.encryption.algorithm" class="form-input">
                <option value="">— {{ $t('actions.none') }} —</option>
                <option value="AES256">AES256</option>
                <option value="CHACHA20">ChaCha20</option>
              </select>
            </div>
          </fieldset>

          <fieldset>
            <legend>{{ $t('storagePolicies.erasureCoding') }}</legend>
            <div class="form-row">
              <div class="form-group">
                <label>{{ $t('storagePolicies.dataBlocks') }}</label>
                <input v-model="form.erasureCoding.dataBlocks" class="form-input" type="number" min="1" />
              </div>
              <div class="form-group">
                <label>{{ $t('storagePolicies.parityBlocks') }}</label>
                <input v-model="form.erasureCoding.parityBlocks" class="form-input" type="number" min="0" />
              </div>
            </div>
          </fieldset>

          <fieldset>
            <legend>{{ $t('storagePolicies.replication') }}</legend>
            <div class="form-group">
              <label>{{ $t('storagePolicies.replicationFactor') }}</label>
              <input v-model="form.replication.factor" class="form-input" type="number" min="1" max="10" />
            </div>
          </fieldset>

          <div class="form-actions">
            <button type="button" class="btn-cancel" @click="closeModal">{{ $t('actions.cancel') }}</button>
            <button type="submit" class="btn-save">{{ $t('actions.save') }}</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Delete Confirmation -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="closeDeleteConfirm">
      <div class="modal-card modal-small">
        <div class="modal-header">
          <h3>{{ $t('storagePolicies.deleteConfirm') }}</h3>
          <button class="modal-close" @click="closeDeleteConfirm">✕</button>
        </div>
        <div class="modal-body">
          <p>{{ $t('storagePolicies.deleteWarning', { storageClassId: policyToDelete?.storageClassId }) }}</p>
        </div>
        <div class="form-actions">
          <button class="btn-cancel" @click="closeDeleteConfirm">{{ $t('actions.cancel') }}</button>
          <button class="btn-delete" @click="deletePolicy">{{ $t('actions.delete') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

const policies = ref([])
const loading = ref(true)
const error = ref(null)
const showModal = ref(false)
const isEditing = ref(false)
const showDeleteConfirm = ref(false)
const policyToDelete = ref(null)

const emptyForm = () => ({
  storageClassId: '',
  dedup: { algorithm: 'SHA256', scope: 'OBJECT', chunkSize: 1048576, alignment: 'NONE' },
  compression: { algorithm: 'GZIP', level: '' },
  encryption: { algorithm: '' },
  erasureCoding: { dataBlocks: '', parityBlocks: '' },
  replication: { factor: '' },
})

const form = ref(emptyForm())

const API_BASE = '/admin'

async function fetchPolicies() {
  loading.value = true
  error.value = null
  try {
    const res = await fetch(`${API_BASE}/storage-policies`)
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    policies.value = await res.json()
  } catch (e) {
    error.value = e.message || t('errors.general')
  } finally {
    loading.value = false
  }
}

function openCreateForm() {
  isEditing.value = false
  form.value = emptyForm()
  showModal.value = true
}

function openEditForm(policy) {
  isEditing.value = true
  form.value = {
    storageClassId: policy.storageClassId || '',
    dedup: policy.dedup
      ? { algorithm: policy.dedup.algorithm || 'SHA256', scope: policy.dedup.scope || 'OBJECT', chunkSize: policy.dedup.chunkSize ?? 1048576, alignment: policy.dedup.alignment || 'NONE' }
      : { algorithm: 'SHA256', scope: 'OBJECT', chunkSize: 1048576, alignment: 'NONE' },
    compression: policy.compression
      ? { algorithm: policy.compression.algorithm || 'GZIP', level: policy.compression.level ?? '' }
      : { algorithm: 'GZIP', level: '' },
    encryption: policy.encryption
      ? { algorithm: policy.encryption.algorithm || '' }
      : { algorithm: '' },
    erasureCoding: policy.erasureCoding
      ? { dataBlocks: policy.erasureCoding.dataBlocks ?? '', parityBlocks: policy.erasureCoding.parityBlocks ?? '' }
      : { dataBlocks: '', parityBlocks: '' },
    replication: policy.replication
      ? { factor: policy.replication.factor ?? '' }
      : { factor: '' },
  }
  showModal.value = true
}

function closeModal() {
  showModal.value = false
}

function buildPayload() {
  const payload = {
    storageClassId: form.value.storageClassId,
    dedup: form.value.dedup.algorithm
      ? {
          algorithm: form.value.dedup.algorithm,
          scope: form.value.dedup.scope,
          chunkSize: Number(form.value.dedup.chunkSize),
          alignment: form.value.dedup.alignment
        }
      : null,
    compression: form.value.compression.algorithm
      ? { algorithm: form.value.compression.algorithm, level: form.value.compression.level !== '' ? Number(form.value.compression.level) : null }
      : null,
    encryption: form.value.encryption.algorithm
      ? { algorithm: form.value.encryption.algorithm }
      : null,
    erasureCoding: form.value.erasureCoding.dataBlocks !== ''
      ? { dataBlocks: Number(form.value.erasureCoding.dataBlocks), parityBlocks: Number(form.value.erasureCoding.parityBlocks) }
      : null,
    replication: form.value.replication.factor !== ''
      ? { factor: Number(form.value.replication.factor) }
      : null,
  }
  return payload
}

async function savePolicy() {
  const isNew = !isEditing.value
  const url = isNew
    ? `${API_BASE}/storage-policies`
    : `${API_BASE}/storage-policies/${form.value.storageClassId}`
  const method = isNew ? 'POST' : 'PUT'

  try {
    const payload = buildPayload()
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    closeModal()
    await fetchPolicies()
  } catch (e) {
    error.value = e.message || t('errors.general')
  }
}

function confirmDelete(policy) {
  policyToDelete.value = policy
  showDeleteConfirm.value = true
}

function closeDeleteConfirm() {
  showDeleteConfirm.value = false
  policyToDelete.value = null
}

async function deletePolicy() {
  if (!policyToDelete.value) return
  try {
    const res = await fetch(`${API_BASE}/storage-policies/${policyToDelete.value.storageClassId}`, {
      method: 'DELETE',
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    closeDeleteConfirm()
    await fetchPolicies()
  } catch (e) {
    error.value = e.message || t('errors.general')
  }
}

onMounted(fetchPolicies)
</script>

<style scoped>
.storage-policies {
  padding: 2rem;
}

.sp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.sp-title {
  font-size: 1.6rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-teal) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.sp-header-actions {
  display: flex;
  gap: 0.5rem;
}

.sp-dashboard-btn {
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  background: var(--accent-teal);
  color: #fff;
  border: 1px solid var(--accent-teal);
  transition: all 0.2s;
}

.sp-dashboard-btn:hover {
  background: var(--accent);
  box-shadow: 0 0 16px var(--accent-glow);
}

.sp-create-btn {
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  background: var(--accent);
  color: #fff;
  transition: all 0.2s;
}

.sp-create-btn:hover {
  background: var(--accent-teal);
  box-shadow: 0 0 16px var(--accent-glow);
}

/* Loading */
.sp-loading {
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
.sp-error {
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

.sp-retry-btn {
  margin-left: auto;
  padding: 0.35rem 0.9rem;
  border-radius: 6px;
  font-size: 0.8rem;
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

/* Table */
.sp-table-container {
  overflow-x: auto;
}

.sp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.sp-table th {
  text-align: left;
  padding: 0.75rem 1rem;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-glass);
  white-space: nowrap;
}

.sp-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border-glass);
  vertical-align: middle;
}

.sp-table tbody tr:hover {
  background: var(--bg-card-hover);
}

.cell-id {
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-mono);
}

.cell-nested {
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--text-secondary);
}

.cell-na {
  color: var(--text-muted);
  font-style: italic;
}

.th-actions {
  text-align: right;
}

.cell-actions {
  text-align: right;
  white-space: nowrap;
}

.action-btn {
  padding: 0.3rem 0.7rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  transition: all 0.2s;
}

.action-btn.edit {
  background: rgba(124, 92, 255, 0.15);
  color: var(--accent);
  border: 1px solid rgba(124, 92, 255, 0.25);
  margin-right: 0.35rem;
}

.action-btn.edit:hover {
  background: var(--accent);
  color: #fff;
}

.action-btn.delete {
  background: rgba(255, 92, 92, 0.15);
  color: var(--danger);
  border: 1px solid rgba(255, 92, 92, 0.25);
}

.action-btn.delete:hover {
  background: var(--danger);
  color: #fff;
}

/* Empty state */
.sp-empty {
  text-align: center;
  padding: 3rem;
  color: var(--text-muted);
  font-size: 0.95rem;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-glass);
  border-radius: 16px;
  width: 90%;
  max-width: 680px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 2rem;
  backdrop-filter: blur(12px);
}

.modal-small {
  max-width: 420px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}

.modal-header h3 {
  font-size: 1.2rem;
  font-weight: 700;
}

.modal-close {
  background: transparent;
  color: var(--text-secondary);
  font-size: 1.25rem;
  padding: 0.25rem;
  transition: color 0.2s;
}

.modal-close:hover {
  color: var(--text-primary);
}

.modal-body {
  margin-bottom: 1.5rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* Form */
.modal-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.modal-form fieldset {
  border: 1px solid var(--border-glass);
  border-radius: 10px;
  padding: 1rem;
}

.modal-form legend {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-secondary);
  padding: 0 0.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-row {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.form-row .form-group {
  flex: 1;
  min-width: 120px;
}

.form-group label {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-secondary);
}

.form-input {
  padding: 0.6rem 0.75rem;
  border-radius: 8px;
  border: 1px solid var(--border-glass);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 0.9rem;
  transition: border-color 0.2s;
}

.form-input:focus {
  border-color: var(--accent);
  outline: none;
}

select.form-input {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
  background-color: var(--bg-card);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath fill='%239898b0' d='M6 8L0 0h12z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 0.75rem center;
  background-size: 12px;
  padding-right: 2rem;
  color: var(--text-primary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.btn-cancel {
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  font-size: 0.85rem;
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border-glass);
  transition: all 0.2s;
}

.btn-cancel:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.btn-save {
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  background: var(--accent);
  color: #fff;
  transition: all 0.2s;
}

.btn-save:hover {
  background: var(--accent-teal);
  box-shadow: 0 0 16px var(--accent-glow);
}

.btn-delete {
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  background: var(--danger);
  color: #fff;
  transition: all 0.2s;
}

.btn-delete:hover {
  background: #ff3b3b;
}
</style>
