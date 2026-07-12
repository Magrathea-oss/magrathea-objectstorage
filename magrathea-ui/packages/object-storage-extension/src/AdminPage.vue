<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ShellBadge, ShellBanner, ShellCard, ShellDisclosure, ShellPageState } from '@magrathea/product-shell'
import { AdminClientError, type OperationalReportType, type StoragePolicyProposal } from './adapters/admin-api-client'
import { adminApiClientKey, s3HeadObjectClientKey } from './context'
import { storagePolicyDetailRoute } from './storage-policy-routes'

const route = useRoute()
const router = useRouter()
const injectedClient = inject(adminApiClientKey)
const s3Client = inject(s3HeadObjectClientKey, undefined)
if (!injectedClient) throw new Error('Object Storage extension requires an AdminApiClient')
const client = injectedClient

const kind = computed(() => String(route.meta.kind || 'dashboard'))
const loading = ref(false)
const operationLoading = ref(false)
const error = ref<unknown>()
const operationError = ref<unknown>()
const data = ref<any>()
const reports = ref<Record<string, { state: 'ready' | 'unavailable' | 'error'; value?: unknown; message?: string }>>({})
const bucket = ref(String(route.params.bucket || ''))
const quotaBytes = ref<number | undefined>()
const proposal = ref({ storageClassId: '', dataBlocks: 8, parityBlocks: 4, replicationFactor: 1 })
const policyCatalogEvidence = ref<{ before: readonly string[]; after: readonly string[]; unchanged: boolean }>()
const diagnostic = ref({ credentialProfile: '', bucket: '', key: '' })

const title = computed(() => ({
  dashboard: 'Admin dashboard', backend: 'Backend status', policies: 'Storage policies',
  'policy-detail': data.value?.storageClassId || String(route.params.id), 'policy-validation': 'Validate a policy proposal',
  devices: 'Storage devices', 'device-detail': data.value?.id || String(route.params.id),
  'disk-sets': 'Disk-set topology', 'disk-set-detail': data.value?.name || String(route.params.id),
  capacity: 'Bucket capacity lookup', 'capacity-detail': `Capacity: ${String(route.params.bucket)}`,
  'data-hygiene': 'Data hygiene', observability: 'Observability', diagnostics: 'S3 HeadObject diagnostic',
}[kind.value] || 'Object Storage administration'))

const description = computed(() => ({
  policies: 'Read-only catalog supplied by the Admin Control Plane.',
  'policy-detail': 'Configured storage pipeline; mutation is intentionally unavailable.',
  'policy-validation': 'Validate a proposal without changing the YAML-backed catalog.',
  devices: 'Read-only capacity, health, and eligibility evidence.',
  'disk-sets': 'Read-only failure-domain membership and topology.',
  capacity: 'Capacity accounting and quota status only. This is not a bucket or object browser.',
  'data-hygiene': 'Recovery, garbage-collection, and scrub evidence from configured providers only.',
  observability: 'Authorized audit, metric, and trace evidence from configured providers only.',
  diagnostics: 'An ordinary signed request to the separately configured S3 Data Plane.',
}[kind.value] || 'Truthful Admin Control Plane evidence.'))

defineExpose({ title, description })

function pageState(): 'offline' | 'unavailable' | 'not-found' | 'error' {
  if (error.value instanceof AdminClientError) {
    if (error.value.kind === 'offline') return 'offline'
    if (error.value.context.status === 404) return 'not-found'
    if (error.value.context.status === 503) return 'unavailable'
  }
  return 'error'
}
function errorMessage(): string { return error.value instanceof Error ? error.value.message : 'The requested evidence could not be loaded.' }
function bytes(value: unknown): string { return typeof value === 'number' ? `${value.toLocaleString()} bytes` : 'Unavailable' }
function eligibility(value: unknown): string {
  if (value === true) return 'Eligible'
  if (value === false) return 'Not eligible'
  return 'Unknown — not returned by the Admin API'
}
const catalogLabels: Readonly<Record<string, string>> = Object.freeze({
  policies: 'Policy catalog',
  devices: 'Device catalog',
  diskSets: 'Disk-set catalog',
})

async function load(): Promise<void> {
  loading.value = true; error.value = undefined; data.value = undefined; reports.value = {}
  try {
    const id = String(route.params.id || '')
    if (kind.value === 'dashboard') {
      const [health, liveness, readiness, backend, devices] = await Promise.all([
        client.getHealth(), client.getLiveness(), client.getReadiness(), client.getBackendStatus(), client.listDevices(),
      ])
      data.value = { health, liveness, readiness, backend: backend || {}, devices: devices || { storageDevices: [] } }
    }
    else if (kind.value === 'backend') data.value = await client.getBackendStatus()
    else if (kind.value === 'policies') data.value = await client.listPolicies()
    else if (kind.value === 'policy-detail') data.value = await client.getPolicy(id)
    else if (kind.value === 'devices') data.value = await client.listDevices()
    else if (kind.value === 'device-detail') data.value = await client.getDevice(id)
    else if (kind.value === 'disk-sets') data.value = await client.listDiskSets()
    else if (kind.value === 'disk-set-detail') data.value = await client.getDiskSet(id)
    else if (kind.value === 'capacity-detail') data.value = await client.getBucketCapacity(String(route.params.bucket))
    else if (kind.value === 'data-hygiene') await loadReports(['recovery', 'garbage-collection', 'scrub'])
    else if (kind.value === 'observability') await loadReports(['audit', 'metrics', 'traces'])
  } catch (caught) { error.value = caught }
  finally { loading.value = false }
}
async function loadReports(types: OperationalReportType[]): Promise<void> {
  await Promise.all(types.map(async (type) => {
    try { reports.value[type] = { state: 'ready', value: await client.getOperationalReport(type) } }
    catch (caught) {
      const unavailable = caught instanceof AdminClientError && caught.context.code === 'report-provider-not-configured'
      reports.value[type] = { state: unavailable ? 'unavailable' : 'error', message: caught instanceof Error ? caught.message : 'Report unavailable' }
    }
  }))
}
async function validatePolicy(): Promise<void> {
  const payload: StoragePolicyProposal = { storageClassId: proposal.value.storageClassId, erasureCoding: { dataBlocks: proposal.value.dataBlocks, parityBlocks: proposal.value.parityBlocks }, replication: { factor: proposal.value.replicationFactor } }
  if (operationLoading.value) return
  operationLoading.value = true; operationError.value = undefined; policyCatalogEvidence.value = undefined; data.value = undefined
  try {
    const before = await policyIds()
    data.value = await client.validatePolicy(payload)
    const after = await policyIds()
    policyCatalogEvidence.value = { before, after, unchanged: JSON.stringify(before) === JSON.stringify(after) }
  } catch (caught) { operationError.value = caught } finally { operationLoading.value = false }
}
async function policyIds(): Promise<readonly string[]> {
  const catalog = await client.listPolicies()
  return [...catalog.storagePolicies.map((policy) => policy.storageClassId)].sort()
}
async function lookupCapacity(): Promise<void> { if (bucket.value.trim()) await router.push(`/admin/capacity/${encodeURIComponent(bucket.value.trim())}`) }
async function updateQuota(): Promise<void> {
  if (quotaBytes.value === undefined) return
  loading.value = true
  try { data.value = await client.configureBucketQuota(String(route.params.bucket), quotaBytes.value) } catch (caught) { error.value = caught } finally { loading.value = false }
}
function reportImpact(type: string): string {
  if (type === 'metrics') return 'Current runtime trends and alert evidence cannot be assessed on this page.'
  if (type === 'recovery') return 'Recovery findings cannot be reviewed from the Admin Control Plane.'
  if (type === 'scrub') return 'At-rest integrity findings and the latest scrub outcome cannot be verified here.'
  if (type === 'garbage-collection') return 'Reclamation findings and outcomes cannot be reviewed here.'
  if (type === 'audit') return 'Administrative audit evidence cannot be reviewed here.'
  return 'Provider-backed operational evidence cannot be shown on this page.'
}
function backendAttention(status: any): Array<{ name: string; message: string }> {
  if (!status) return []
  const findings: Array<{ name: string; message: string }> = []
  for (const [name, catalog] of Object.entries(status.catalogs || {}) as Array<[string, any]>) {
    if (catalog.availability !== 'available') findings.push({ name: `${catalogLabels[name] || name}`, message: `Catalog is ${catalog.availability}. ${catalog.error || 'Configuration evidence is unavailable.'}` })
  }
  for (const [name, root] of Object.entries(status.storageRoots || {}) as Array<[string, any]>) {
    if (root.availability !== 'available') findings.push({ name: `${name} storage root`, message: `Storage root is ${root.availability}.` })
  }
  return findings
}
async function runDiagnostic(): Promise<void> {
  if (!s3Client) { error.value = new Error('S3 diagnostics are unavailable because no separately configured S3 client is installed.'); return }
  loading.value = true; error.value = undefined
  try { data.value = await s3Client.headObject(diagnostic.value) } catch (caught) { error.value = caught } finally { loading.value = false }
}

onMounted(load)
watch(() => route.fullPath, load)
</script>

<template>
  <section class="object-storage-page" :aria-labelledby="'object-storage-heading'">
    <h2 id="object-storage-heading" class="visually-hidden">{{ title }}</h2>
    <ShellPageState v-if="loading" state="loading" :heading="`Loading ${title}`" />
    <ShellPageState v-else-if="error" :state="pageState()" :message="errorMessage()" @recover="load" />

    <template v-else-if="kind === 'dashboard' && data">
      <section class="priority-summary" :class="{ 'priority-summary--attention': data.readiness.status !== 'ready' }" aria-labelledby="operational-summary">
        <div><p class="section-kicker">Operational summary</p><h3 id="operational-summary">{{ data.readiness.status === 'ready' ? 'Service is ready' : 'Service requires attention' }}</h3><p>Selected backend: <strong>{{ data.backend.selectedBackend || 'Unavailable' }}</strong></p></div>
        <ShellBadge :tone="data.readiness.status === 'ready' ? 'positive' : 'danger'" :label="data.readiness.status" />
      </section>
      <ShellCard v-if="data.readiness.status !== 'ready'" title="Conditions requiring attention" eyebrow="Act first">
        <ul class="attention-list"><li v-for="item in data.readiness.components.filter((component: any) => component.status !== 'ready')" :key="item.name"><strong>{{ item.name }}</strong><span>{{ item.status }}<template v-if="item.reason"> — {{ item.reason }}</template></span></li></ul>
        <div v-if="data.devices.storageDevices.some((device: any) => String(device.health).toUpperCase() !== 'HEALTHY')" class="context-actions">
          <RouterLink v-for="device in data.devices.storageDevices.filter((item: any) => String(item.health).toUpperCase() !== 'HEALTHY')" :key="device.id" class="shell-button shell-button--secondary" :to="`/admin/storage-devices/${encodeURIComponent(device.id)}`">Inspect degraded device {{ device.id }}</RouterLink>
        </div>
      </ShellCard>
      <div class="card-grid supporting-status">
        <ShellCard title="Admin API" eyebrow="Control Plane"><ShellBadge tone="positive" :label="data.health.status" /><p>{{ data.health.message }}</p></ShellCard>
        <ShellCard title="Process" eyebrow="Liveness"><ShellBadge tone="positive" :label="data.liveness.status" /><p>{{ data.liveness.message }}</p></ShellCard>
      </div>
      <section aria-labelledby="quick-tasks">
<p class="section-kicker">Admin Control Plane</p><h3 id="quick-tasks">Quick tasks</h3><div class="task-grid">
        <RouterLink class="task-link" to="/admin/storage-devices"><strong>Inspect storage</strong><span>Review read-only device and topology evidence.</span></RouterLink>
        <RouterLink class="task-link" to="/admin/storage-policies/validate"><strong>Validate a policy</strong><span>Check a proposal without persisting it.</span></RouterLink>
        <RouterLink class="task-link" to="/admin/capacity"><strong>Manage bucket quota</strong><span>Update capacity limits; no object access.</span></RouterLink>
        <RouterLink class="task-link" to="/admin/docs"><strong>Open documentation</strong><span>Read product and operations guidance.</span></RouterLink>
      </div>
</section>
    </template>

    <template v-else-if="kind === 'backend' && data">
      <section class="priority-summary" aria-labelledby="backend-summary"><div><p class="section-kicker">Runtime selection</p><h3 id="backend-summary">{{ data.selectedBackend || 'Backend unavailable' }}</h3><p>Profile <strong>{{ data.selection?.profile || 'Unavailable' }}</strong></p></div><ShellBadge :tone="backendAttention(data).length ? 'danger' : 'positive'" :label="backendAttention(data).length ? 'Attention required' : 'Available'" /></section>
      <ShellBanner v-for="finding in backendAttention(data)" :key="finding.name" tone="danger" :title="finding.name">{{ finding.message }}</ShellBanner>
      <ShellDisclosure title="Selection diagnostics" summary="Show the property that selected this backend">
        <dl class="facts"><dt>Selecting property</dt><dd>{{ data.selection?.property?.name || 'Unavailable' }} = {{ data.selection?.property?.value || 'Unavailable' }}</dd></dl>
      </ShellDisclosure>
      <ShellDisclosure title="Catalog evidence" summary="Show catalog availability, counts, and source paths">
        <div class="card-grid compact"><article v-for="(catalog, name) in data.catalogs" :key="name"><strong>{{ catalogLabels[String(name)] || name }}</strong><dl class="facts"><dt>Catalog count</dt><dd>{{ catalog.itemCount ?? 'Unavailable' }}</dd><dt>Source path</dt><dd>{{ catalog.sourceDirectory || 'Unavailable — not returned by the Admin API' }}</dd></dl><ShellBadge :tone="catalog.availability === 'available' ? 'positive' : 'warning'" :label="catalog.availability || 'Unavailable'" /></article></div>
      </ShellDisclosure>
      <ShellDisclosure title="Storage-root evidence" summary="Show configured paths and availability">
        <ul><li v-for="(root, name) in data.storageRoots" :key="name"><strong>{{ name }}</strong>: {{ root.path }} — {{ root.availability }}</li></ul>
      </ShellDisclosure>
    </template>

    <template v-else-if="kind === 'policies' && data">
      <ShellBanner tone="info" title="Read-only configuration-as-code">Changes require YAML configuration followed by catalog reload or redeployment. No create, edit, save, or delete actions are available.</ShellBanner>
      <ShellPageState v-if="data.storagePolicies.length === 0" state="empty" heading="No storage policies configured" />
      <div v-else class="list-grid"><RouterLink v-for="policy in data.storagePolicies" :key="policy.storageClassId" class="resource-link" :to="storagePolicyDetailRoute(policy.storageClassId)"><strong>{{ policy.storageClassId }}</strong><span>Replication × {{ policy.replication.factor }}</span></RouterLink></div>
    </template>

    <ShellCard v-else-if="kind === 'policy-detail' && data" :title="data.storageClassId" eyebrow="Read-only policy">
      <ShellBanner tone="info" title="Read-only configuration-as-code">Changes require YAML configuration followed by catalog reload or redeployment.</ShellBanner>
      <div class="pipeline"><article v-for="stage in ['dedup','compression','encryption','erasureCoding','replication']" :key="stage"><h3>{{ stage }}</h3><pre>{{ data[stage] ? JSON.stringify(data[stage], null, 2) : 'Not configured' }}</pre></article></div>
    </ShellCard>

    <ShellCard v-else-if="kind === 'policy-validation'" title="Non-persistent validation" eyebrow="Validation only">
      <ShellBanner tone="warning" title="Validation only — non-persistent">This proposal is never written to the storage-policy catalog.</ShellBanner>
      <form class="form-grid" @submit.prevent="validatePolicy"><label>Storage class ID<input v-model="proposal.storageClassId" required></label><label>Data blocks<input v-model.number="proposal.dataBlocks" type="number" min="1" required></label><label>Parity blocks<input v-model.number="proposal.parityBlocks" type="number" min="0" required></label><label>Replication factor<input v-model.number="proposal.replicationFactor" type="number" min="1" required></label><button class="shell-button" type="submit" :disabled="operationLoading">{{ operationLoading ? 'Validating proposal…' : 'Validate proposal without saving' }}</button></form>
      <p v-if="operationLoading" class="operation-progress" role="status">Validation in progress. The proposal will not be persisted.</p>
      <ShellBanner v-else-if="operationError" tone="danger" title="Policy validation could not be completed" aria-live="assertive">{{ operationError instanceof Error ? operationError.message : 'Check the proposal or connection and try again.' }} Your proposal is preserved. Correct it or retry; the catalog was not changed.</ShellBanner>
      <section v-else-if="data" aria-live="polite"><ShellBanner :tone="data.valid ? 'positive' : 'warning'" :title="`Validation ${data.valid ? 'succeeded' : 'found corrections'}`">Proposal {{ proposal.storageClassId }} was not persisted. The storage-policy catalog was not changed by this validation.</ShellBanner><h3>Validation report: {{ data.valid ? 'Valid' : 'Invalid' }}</h3><ul><li v-for="finding in data.errors" :key="finding.field">{{ finding.field }}: {{ finding.message }}</li></ul><div v-if="policyCatalogEvidence" class="catalog-evidence"><h4>Storage-policy catalog refreshed after validation</h4><p>{{ policyCatalogEvidence.unchanged ? 'Catalog unchanged — the proposal was not persisted.' : 'Catalog changed outside this validation request; review the refreshed catalog.' }}</p><p>Before: {{ policyCatalogEvidence.before.join(', ') || 'No policies' }}</p><p>After refresh: {{ policyCatalogEvidence.after.join(', ') || 'No policies' }}</p></div></section>
    </ShellCard>

    <template v-else-if="kind === 'devices' && data"><ShellBanner tone="info" title="Read-only configuration-as-code">Device catalog mutation is unavailable.</ShellBanner><ShellPageState v-if="!data.storageDevices.length" state="empty" heading="No devices configured" /><div v-else class="list-grid"><RouterLink v-for="device in data.storageDevices" :key="device.id" class="resource-link" :to="`/admin/storage-devices/${encodeURIComponent(device.id)}`"><strong>{{ device.id }}</strong><span>{{ device.health }} · {{ bytes(device.availableCapacityBytes) }} available</span></RouterLink></div></template>
    <ShellCard v-else-if="kind === 'device-detail' && data" :title="data.id" eyebrow="Read-only device"><ShellBanner tone="info" title="Read-only configuration-as-code">No create, edit, retire, or delete action is available.</ShellBanner><dl class="facts"><dt>Path</dt><dd>{{ data.storagePath }}</dd><dt>Health</dt><dd>{{ data.health }}</dd><dt>Total capacity</dt><dd>{{ bytes(data.totalCapacityBytes) }}</dd><dt>Available capacity</dt><dd>{{ bytes(data.availableCapacityBytes) }}</dd><dt>Read eligibility</dt><dd>{{ eligibility(data.readEligible) }}</dd><dt>Write eligibility</dt><dd>{{ eligibility(data.writeEligible) }}</dd></dl></ShellCard>

    <template v-else-if="kind === 'disk-sets' && data"><ShellBanner tone="info" title="Read-only topology">Disk-set and membership mutation is unavailable.</ShellBanner><ShellPageState v-if="!data.diskSets.length" state="empty" heading="No disk sets configured" /><div v-else class="list-grid"><RouterLink v-for="set in data.diskSets" :key="set.name" class="resource-link" :to="`/admin/disk-sets/${encodeURIComponent(set.name)}`"><strong>{{ set.name }}</strong><span>{{ set.failureDomain }} · {{ set.size }} devices</span></RouterLink></div></template>
    <ShellCard v-else-if="kind === 'disk-set-detail' && data" :title="data.name" eyebrow="Read-only topology"><p>Failure domain: <strong>{{ data.failureDomain }}</strong></p><h3>Members</h3><ul><li v-for="device in data.devices" :key="device"><RouterLink :to="`/admin/storage-devices/${encodeURIComponent(device)}`">{{ device }}</RouterLink></li></ul><p>No disk-set or membership mutation action is available.</p></ShellCard>

    <ShellCard v-else-if="kind === 'capacity'" title="Find bucket capacity" eyebrow="Accounting only"><p>This lookup does not list buckets, objects, or object keys.</p><form class="inline-form" @submit.prevent="lookupCapacity"><label>Bucket name<input v-model="bucket" required></label><button class="shell-button" type="submit">Look up capacity</button></form></ShellCard>
    <ShellCard v-else-if="kind === 'capacity-detail' && data" :title="data.bucket" eyebrow="Capacity and quota status"><dl class="facts"><dt>Used</dt><dd>{{ bytes(data.usedBytes) }}</dd><dt>Reserved</dt><dd>{{ bytes(data.reservedBytes) }}</dd><dt>Quota</dt><dd>{{ bytes(data.quotaBytes) }}</dd><dt>Rejected reservations</dt><dd>{{ data.rejectedReservations }}</dd><dt>Last rejected</dt><dd>{{ bytes(data.lastRejectedBytes) }}</dd></dl><form class="inline-form" @submit.prevent="updateQuota"><label>New quota in bytes<input v-model.number="quotaBytes" type="number" min="0" required></label><button class="shell-button" type="submit">Update quota</button></form><p>No bucket or object browsing is available.</p></ShellCard>

    <div v-else-if="kind === 'data-hygiene' || kind === 'observability'" class="card-grid"><ShellCard v-for="(report, name) in reports" :key="name" :title="String(name)" eyebrow="Provider evidence"><template v-if="report.state !== 'ready'"><ShellBadge tone="warning" label="Not configured" /><h2 class="report-state-heading">Provider not configured</h2><h3>{{ String(name) }} evidence unavailable</h3><p>{{ report.message }}</p><p><strong>Operational impact:</strong> {{ reportImpact(String(name)) }}</p><div class="context-actions"><button class="shell-button shell-button--secondary" type="button" @click="load">Retry evidence</button><RouterLink class="text-link" to="/admin/docs">Open configuration documentation</RouterLink></div><p class="unsupported-note">The {{ String(name) }} operation is not available from this page.</p></template><pre v-else>{{ JSON.stringify(report.value, null, 2) }}</pre></ShellCard></div>

    <ShellCard v-else-if="kind === 'diagnostics'" title="Signed HeadObject" eyebrow="S3 Data Plane"><ShellBanner tone="info" title="Separate S3 client">This diagnostic never calls Admin or private storage-engine object endpoints and grants only the selected credential profile's privileges. Credentials and signed headers are never displayed.</ShellBanner><form class="form-grid" @submit.prevent="runDiagnostic"><label>Credential profile<input v-model="diagnostic.credentialProfile" required autocomplete="off"></label><label>Bucket<input v-model="diagnostic.bucket" required></label><label>Object key<input v-model="diagnostic.key" required></label><button class="shell-button" type="submit">Run HeadObject</button></form><dl v-if="data" class="facts" aria-live="polite"><dt>Request method</dt><dd>{{ data.request?.method || 'HEAD' }}</dd><dt>Safe S3 target</dt><dd>{{ data.request?.target || 'Not returned by client' }}</dd><dt>Outcome</dt><dd>{{ data.ok ? 'S3 accepted the request' : 'S3 rejected the request' }}</dd><dt>Status</dt><dd>{{ data.status }} {{ data.statusText }}</dd><dt>S3 request ID</dt><dd>{{ data.requestId || 'Not returned' }}</dd><dt>ETag</dt><dd>{{ data.eTag || 'Not returned' }}</dd><dt>Content length</dt><dd>{{ data.contentLength ?? 'Not returned' }}</dd><dt>S3 error code</dt><dd>{{ data.errorCode || 'Not returned' }}</dd></dl></ShellCard>
  </section>
</template>

<style scoped>
.object-storage-page { display: grid; gap: var(--shell-space-5); }
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.card-grid, .list-grid, .pipeline, .task-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr)); gap: var(--shell-space-4); }
.priority-summary { display: flex; align-items: center; justify-content: space-between; gap: var(--shell-space-4); padding: clamp(var(--shell-space-4), 4vw, var(--shell-space-6)); background: var(--shell-surface-raised); border: 1px solid var(--shell-border); border-left: 0.35rem solid var(--shell-positive); border-radius: var(--shell-radius-md); }
.priority-summary--attention { border-left-color: var(--shell-danger); }
.priority-summary h3, #quick-tasks { margin: 0; font-size: clamp(1.25rem, 3vw, 1.75rem); }.priority-summary p { margin-bottom: 0; }
.section-kicker { margin: 0 0 var(--shell-space-1); color: var(--shell-text-muted); font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.attention-list { display: grid; gap: var(--shell-space-3); padding: 0; list-style: none; }.attention-list li { display: grid; gap: var(--shell-space-1); padding-left: var(--shell-space-3); border-left: 3px solid var(--shell-danger); }
.task-link { display: grid; gap: var(--shell-space-2); min-height: 7rem; padding: var(--shell-space-4); color: var(--shell-text); background: var(--shell-surface-raised); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-md); text-decoration: none; }.task-link:hover, .task-link:focus-visible { border-color: var(--shell-brand); box-shadow: var(--shell-shadow-sm); }.task-link span { color: var(--shell-text-muted); }
.context-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--shell-space-3); margin-top: var(--shell-space-4); }.text-link { color: var(--shell-link, var(--shell-brand)); font-weight: 700; text-underline-offset: .2em; }.unsupported-note { color: var(--shell-text-muted); font-size: var(--shell-text-sm); }.operation-progress { padding: var(--shell-space-3); border-left: 3px solid var(--shell-info); }
.card-grid.compact article { padding: var(--shell-space-4); background: var(--shell-surface-canvas); border-radius: var(--shell-radius-sm); overflow-wrap: anywhere; }
.resource-link { display: grid; gap: var(--shell-space-2); min-height: 5rem; padding: var(--shell-space-4); color: var(--shell-text); background: var(--shell-surface-raised); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-md); text-decoration: none; }
.resource-link:hover, .resource-link:focus-visible { border-color: var(--shell-brand); }
.resource-link span { color: var(--shell-text-muted); }
.facts { display: grid; grid-template-columns: minmax(8rem, 0.5fr) minmax(0, 1fr); gap: var(--shell-space-2) var(--shell-space-4); }
.facts dt { font-weight: 700; }.facts dd { margin: 0; overflow-wrap: anywhere; }
.form-grid, .inline-form { display: flex; flex-wrap: wrap; align-items: end; gap: var(--shell-space-4); margin-block: var(--shell-space-5); }
.form-grid label, .inline-form label { display: grid; flex: 1 1 12rem; gap: var(--shell-space-2); font-weight: 700; }
input { min-height: 2.75rem; width: 100%; padding: var(--shell-space-3); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-sm); }
pre { max-width: 100%; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 30rem) { .facts { grid-template-columns: 1fr; }.facts dd { margin-bottom: var(--shell-space-3); } }
</style>
