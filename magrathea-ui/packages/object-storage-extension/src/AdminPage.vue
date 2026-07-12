<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ShellBadge, ShellBanner, ShellCard, ShellPageState } from '@magrathea/product-shell'
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
const error = ref<unknown>()
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
    if (kind.value === 'dashboard') data.value = await Promise.all([client.getHealth(), client.getLiveness(), client.getReadiness()])
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
  loading.value = true; error.value = undefined; policyCatalogEvidence.value = undefined
  try {
    const before = await policyIds()
    data.value = await client.validatePolicy(payload)
    const after = await policyIds()
    policyCatalogEvidence.value = { before, after, unchanged: JSON.stringify(before) === JSON.stringify(after) }
  } catch (caught) { error.value = caught } finally { loading.value = false }
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
      <div class="card-grid">
        <ShellCard title="Admin API" eyebrow="Health"><ShellBadge tone="positive" :label="data[0].status" /><p>{{ data[0].message }}</p><p>Mode: {{ data[0].mode }}</p></ShellCard>
        <ShellCard title="Liveness" eyebrow="Process"><ShellBadge tone="positive" :label="data[1].status" /><p>{{ data[1].message }}</p></ShellCard>
        <ShellCard title="Readiness" eyebrow="Dependencies"><ShellBadge :tone="data[2].status === 'ready' ? 'positive' : 'warning'" :label="data[2].status" /><ul><li v-for="item in data[2].components" :key="item.name"><strong>{{ item.name }}</strong>: {{ item.status }}<span v-if="item.reason"> — {{ item.reason }}</span></li></ul></ShellCard>
      </div>
    </template>

    <ShellCard v-else-if="kind === 'backend' && data" title="Runtime selection" eyebrow="Configuration evidence">
      <dl class="facts"><dt>Selected backend</dt><dd>{{ data.selectedBackend || 'Unavailable' }}</dd><dt>Profile</dt><dd>{{ data.selection?.profile || 'Unavailable' }}</dd><dt>Selecting property</dt><dd>{{ data.selection?.property?.name || 'Unavailable' }} = {{ data.selection?.property?.value || 'Unavailable' }}</dd></dl>
      <h3>Catalogs</h3><div class="card-grid compact"><article v-for="(catalog, name) in data.catalogs" :key="name"><strong>{{ catalogLabels[String(name)] || name }}</strong><dl class="facts"><dt>Catalog count</dt><dd>{{ catalog.itemCount ?? 'Unavailable' }}</dd><dt>Source path</dt><dd>{{ catalog.sourceDirectory || 'Unavailable — not returned by the Admin API' }}</dd></dl><ShellBadge :label="catalog.availability || 'Unavailable'" /></article></div>
      <h3>Storage roots</h3><ul><li v-for="(root, name) in data.storageRoots" :key="name"><strong>{{ name }}</strong>: {{ root.path }} — {{ root.availability }}</li></ul>
    </ShellCard>

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
      <form class="form-grid" @submit.prevent="validatePolicy"><label>Storage class ID<input v-model="proposal.storageClassId" required></label><label>Data blocks<input v-model.number="proposal.dataBlocks" type="number" min="1" required></label><label>Parity blocks<input v-model.number="proposal.parityBlocks" type="number" min="0" required></label><label>Replication factor<input v-model.number="proposal.replicationFactor" type="number" min="1" required></label><button class="shell-button" type="submit">Validate proposal</button></form>
      <section v-if="data" aria-live="polite"><h3>Validation report: {{ data.valid ? 'Valid' : 'Invalid' }}</h3><p>This report is non-persistent.</p><ul><li v-for="finding in data.errors" :key="finding.field">{{ finding.field }}: {{ finding.message }}</li></ul><div v-if="policyCatalogEvidence" class="catalog-evidence"><h4>Storage-policy catalog refreshed after validation</h4><p>{{ policyCatalogEvidence.unchanged ? 'Catalog unchanged — the proposal was not persisted.' : 'Catalog changed outside this validation request; review the refreshed catalog.' }}</p><p>Before: {{ policyCatalogEvidence.before.join(', ') || 'No policies' }}</p><p>After refresh: {{ policyCatalogEvidence.after.join(', ') || 'No policies' }}</p></div></section>
    </ShellCard>

    <template v-else-if="kind === 'devices' && data"><ShellBanner tone="info" title="Read-only configuration-as-code">Device catalog mutation is unavailable.</ShellBanner><ShellPageState v-if="!data.storageDevices.length" state="empty" heading="No devices configured" /><div v-else class="list-grid"><RouterLink v-for="device in data.storageDevices" :key="device.id" class="resource-link" :to="`/admin/storage-devices/${encodeURIComponent(device.id)}`"><strong>{{ device.id }}</strong><span>{{ device.health }} · {{ bytes(device.availableCapacityBytes) }} available</span></RouterLink></div></template>
    <ShellCard v-else-if="kind === 'device-detail' && data" :title="data.id" eyebrow="Read-only device"><ShellBanner tone="info" title="Read-only configuration-as-code">No create, edit, retire, or delete action is available.</ShellBanner><dl class="facts"><dt>Path</dt><dd>{{ data.storagePath }}</dd><dt>Health</dt><dd>{{ data.health }}</dd><dt>Total capacity</dt><dd>{{ bytes(data.totalCapacityBytes) }}</dd><dt>Available capacity</dt><dd>{{ bytes(data.availableCapacityBytes) }}</dd><dt>Read eligibility</dt><dd>{{ eligibility(data.readEligible) }}</dd><dt>Write eligibility</dt><dd>{{ eligibility(data.writeEligible) }}</dd></dl></ShellCard>

    <template v-else-if="kind === 'disk-sets' && data"><ShellBanner tone="info" title="Read-only topology">Disk-set and membership mutation is unavailable.</ShellBanner><ShellPageState v-if="!data.diskSets.length" state="empty" heading="No disk sets configured" /><div v-else class="list-grid"><RouterLink v-for="set in data.diskSets" :key="set.name" class="resource-link" :to="`/admin/disk-sets/${encodeURIComponent(set.name)}`"><strong>{{ set.name }}</strong><span>{{ set.failureDomain }} · {{ set.size }} devices</span></RouterLink></div></template>
    <ShellCard v-else-if="kind === 'disk-set-detail' && data" :title="data.name" eyebrow="Read-only topology"><p>Failure domain: <strong>{{ data.failureDomain }}</strong></p><h3>Members</h3><ul><li v-for="device in data.devices" :key="device"><RouterLink :to="`/admin/storage-devices/${encodeURIComponent(device)}`">{{ device }}</RouterLink></li></ul><p>No disk-set or membership mutation action is available.</p></ShellCard>

    <ShellCard v-else-if="kind === 'capacity'" title="Find bucket capacity" eyebrow="Accounting only"><p>This lookup does not list buckets, objects, or object keys.</p><form class="inline-form" @submit.prevent="lookupCapacity"><label>Bucket name<input v-model="bucket" required></label><button class="shell-button" type="submit">Look up capacity</button></form></ShellCard>
    <ShellCard v-else-if="kind === 'capacity-detail' && data" :title="data.bucket" eyebrow="Capacity and quota status"><dl class="facts"><dt>Used</dt><dd>{{ bytes(data.usedBytes) }}</dd><dt>Reserved</dt><dd>{{ bytes(data.reservedBytes) }}</dd><dt>Quota</dt><dd>{{ bytes(data.quotaBytes) }}</dd><dt>Rejected reservations</dt><dd>{{ data.rejectedReservations }}</dd><dt>Last rejected</dt><dd>{{ bytes(data.lastRejectedBytes) }}</dd></dl><form class="inline-form" @submit.prevent="updateQuota"><label>New quota in bytes<input v-model.number="quotaBytes" type="number" min="0" required></label><button class="shell-button" type="submit">Update quota</button></form><p>No bucket or object browsing is available.</p></ShellCard>

    <div v-else-if="kind === 'data-hygiene' || kind === 'observability'" class="card-grid"><ShellCard v-for="(report, name) in reports" :key="name" :title="String(name)" eyebrow="Provider evidence"><ShellPageState v-if="report.state !== 'ready'" :state="report.state === 'unavailable' ? 'unavailable' : 'error'" :heading="report.state === 'unavailable' ? 'Provider not configured' : 'Report unavailable'" :message="report.message" @recover="load" /><pre v-else>{{ JSON.stringify(report.value, null, 2) }}</pre></ShellCard></div>

    <ShellCard v-else-if="kind === 'diagnostics'" title="Signed HeadObject" eyebrow="S3 Data Plane"><ShellBanner tone="info" title="Separate S3 client">This diagnostic never calls Admin or private storage-engine object endpoints and grants only the selected credential profile's privileges. Credentials and signed headers are never displayed.</ShellBanner><form class="form-grid" @submit.prevent="runDiagnostic"><label>Credential profile<input v-model="diagnostic.credentialProfile" required autocomplete="off"></label><label>Bucket<input v-model="diagnostic.bucket" required></label><label>Object key<input v-model="diagnostic.key" required></label><button class="shell-button" type="submit">Run HeadObject</button></form><dl v-if="data" class="facts" aria-live="polite"><dt>Request method</dt><dd>{{ data.request?.method || 'HEAD' }}</dd><dt>Safe S3 target</dt><dd>{{ data.request?.target || 'Not returned by client' }}</dd><dt>Outcome</dt><dd>{{ data.ok ? 'S3 accepted the request' : 'S3 rejected the request' }}</dd><dt>Status</dt><dd>{{ data.status }} {{ data.statusText }}</dd><dt>S3 request ID</dt><dd>{{ data.requestId || 'Not returned' }}</dd><dt>ETag</dt><dd>{{ data.eTag || 'Not returned' }}</dd><dt>Content length</dt><dd>{{ data.contentLength ?? 'Not returned' }}</dd><dt>S3 error code</dt><dd>{{ data.errorCode || 'Not returned' }}</dd></dl></ShellCard>
  </section>
</template>

<style scoped>
.object-storage-page { display: grid; gap: var(--shell-space-5); }
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.card-grid, .list-grid, .pipeline { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr)); gap: var(--shell-space-4); }
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
