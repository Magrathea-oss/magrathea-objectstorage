<template>
  <div class="storage-policies">
    <!-- Header -->
    <div class="sp-header">
      <h2 class="sp-title">{{ $t('storagePolicies.title') }}</h2>
      <button class="sp-create-btn" @click="openCreateForm">
        {{ $t('storagePolicies.create') }}
      </button>
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
            <th>{{ $t('storagePolicies.name') }}</th>
            <th>{{ $t('storagePolicies.type') }}</th>
            <th>{{ $t('storagePolicies.description') }}</th>
            <th>{{ $t('storagePolicies.bucket') }}</th>
            <th class="th-actions">{{ $t('actions.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="policy in policies" :key="policy.id">
            <td class="cell-name">{{ policy.name }}</td>
            <td>
              <span :class="['policy-badge', policy.type]">{{ policy.type }}</span>
            </td>
            <td class="cell-desc">{{ policy.description || '—' }}</td>
            <td>{{ policy.bucketName || '—' }}</td>
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
            <label>{{ $t('storagePolicies.name') }}</label>
            <input v-model="form.name" class="form-input" required />
          </div>
          <div class="form-group">
            <label>{{ $t('storagePolicies.type') }}</label>
            <select v-model="form.type" class="form-input" required>
              <option value="RETENTION">Retention</option>
              <option value="REPLICATION">Replication</option>
              <option value="TIERED">Tiered</option>
              <option value="COMPLIANCE">Compliance</option>
            </select>
          </div>
          <div class="form-group">
            <label>{{ $t('storagePolicies.description') }}</label>
            <textarea v-model="form.description" class="form-input" rows="3"></textarea>
          </div>
          <div class="form-group">
            <label>{{ $t('storagePolicies.bucket') }}</label>
            <input v-model="form.bucketName" class="form-input" placeholder="Optional bucket name" />
          </div>
          <div class="form-group">
            <label>{{ $t('storagePolicies.configuration') }}</label>
            <textarea v-model="form.configuration" class="form-input code-input" rows="6" placeholder="JSON configuration"></textarea>
          </div>
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
          <p>{{ $t('storagePolicies.deleteWarning', { name: policyToDelete?.name }) }}</p>
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

const { t } = useI18n()

const policies = ref([])
const loading = ref(true)
const error = ref(null)
const showModal = ref(false)
const isEditing = ref(false)
const showDeleteConfirm = ref(false)
const policyToDelete = ref(null)

const form = ref({
  id: null,
  name: '',
  type: 'RETENTION',
  description: '',
  bucketName: '',
  configuration: '',
})

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
  form.value = { id: null, name: '', type: 'RETENTION', description: '', bucketName: '', configuration: '' }
  showModal.value = true
}

function openEditForm(policy) {
  isEditing.value = true
  form.value = {
    id: policy.id,
    name: policy.name || '',
    type: policy.type || 'RETENTION',
    description: policy.description || '',
    bucketName: policy.bucketName || '',
    configuration: policy.configuration || '',
  }
  showModal.value = true
}

function closeModal() {
  showModal.value = false
}

async function savePolicy() {
  const isNew = !form.value.id
  const url = `${API_BASE}/storage-policies${isNew ? '' : `/${form.value.id}`}`
  const method = isNew ? 'POST' : 'PUT'

  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: form.value.name,
        type: form.value.type,
        description: form.value.description,
        bucketName: form.value.bucketName || null,
        configuration: form.value.configuration ? JSON.parse(form.value.configuration) : {},
      }),
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
    const res = await fetch(`${API_BASE}/storage-policies/${policyToDelete.value.id}`, {
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
}

.sp-title {
  font-size: 1.6rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-teal) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
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

.cell-name {
  font-weight: 600;
  color: var(--text-primary);
}

.cell-desc {
  color: var(--text-secondary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.policy-badge {
  display: inline-block;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 500;
  text-transform: uppercase;
}

.policy-badge.RETENTION {
  background: rgba(124, 92, 255, 0.15);
  color: var(--accent);
  border: 1px solid rgba(124, 92, 255, 0.25);
}

.policy-badge.REPLICATION {
  background: rgba(54, 214, 184, 0.15);
  color: var(--accent-teal);
  border: 1px solid rgba(54, 214, 184, 0.25);
}

.policy-badge.TIERED {
  background: rgba(255, 122, 92, 0.15);
  color: var(--accent-orange);
  border: 1px solid rgba(255, 122, 92, 0.25);
}

.policy-badge.COMPLIANCE {
  background: rgba(92, 255, 122, 0.15);
  color: var(--accent-green);
  border: 1px solid rgba(92, 255, 122, 0.25);
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
  max-width: 600px;
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
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
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

.form-input.code-input {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  resize: vertical;
}

select.form-input {
  appearance: none;
  cursor: pointer;
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
