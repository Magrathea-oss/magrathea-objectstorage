import type { ProductExtension } from '@magrathea/product-shell'
import AdminPage from './AdminPage.vue'

const pages = [
  ['dashboard', '/admin', 'objectStorage.nav.dashboard', 'dashboard', 'health', 'overview'],
  ['backend', '/admin/backend-status', 'objectStorage.nav.backend', 'backend', 'health', 'gauge'],
  ['data-hygiene', '/admin/data-hygiene', 'objectStorage.nav.dataHygiene', 'data-hygiene', 'health', 'operations'],
  ['observability', '/admin/observability', 'objectStorage.nav.observability', 'observability', 'health', 'activity'],
  ['policies', '/admin/storage-policies', 'objectStorage.nav.policies', 'policies', 'storage', 'layers'],
  ['devices', '/admin/storage-devices', 'objectStorage.nav.devices', 'devices', 'storage', 'gauge'],
  ['disk-sets', '/admin/disk-sets', 'objectStorage.nav.diskSets', 'disk-sets', 'storage', 'layers'],
  ['policy-validation', '/admin/storage-policies/validate', 'objectStorage.nav.validatePolicy', 'policy-validation', 'configuration', 'checklist'],
  ['capacity', '/admin/capacity', 'objectStorage.nav.capacity', 'capacity', 'configuration', 'settings'],
  ['diagnostics', '/admin/s3-diagnostics', 'objectStorage.nav.diagnostics', 'diagnostics', 'configuration', 'operations'],
] as const

const messages = {
  nav: {
    dashboard: 'Dashboard', backend: 'Backend status', policies: 'Storage policies',
    validatePolicy: 'Validate policy', devices: 'Storage devices', diskSets: 'Disk-set topology',
    capacity: 'Bucket capacity', dataHygiene: 'Data hygiene', observability: 'Observability',
    diagnostics: 'S3 diagnostics', documentation: 'Documentation',
    groups: { health: 'Assess service health', storage: 'Inspect storage', configuration: 'Administer configuration' },
    groupDescriptions: {
      health: 'Prioritize readiness and provider-backed operational evidence.',
      storage: 'Review read-only catalogs, devices, and failure domains.',
      configuration: 'Validate policy, manage quota, and run bounded diagnostics.',
    },
    descriptions: {
      dashboard: 'Readiness, selected backend, and tasks requiring attention.', backend: 'Runtime selection and supporting technical evidence.',
      policies: 'Read-only configuration-as-code catalog.', validatePolicy: 'Check a proposal without persisting it.',
      devices: 'Device health, capacity, and eligibility.', diskSets: 'Read-only failure-domain membership.',
      'disk-sets': 'Read-only failure-domain membership.',
      capacity: 'Admin Control Plane quota accounting.', dataHygiene: 'Recovery, garbage-collection, and scrub evidence.',
      'data-hygiene': 'Recovery, garbage-collection, and scrub evidence.',
      observability: 'Audit, metrics, and trace provider evidence.', diagnostics: 'A bounded S3 HeadObject diagnostic.',
      documentation: 'Product, operations, and API guidance.',
    },
  },
}

export const objectStorageEnglishMessages = Object.freeze(messages)

export function createObjectStorageExtension(): ProductExtension {
  return {
    id: 'object-storage',
    order: 10,
    capabilities: [{ id: 'object-storage-admin', description: 'Object Storage Admin Control Plane' }],
    navigationGroups: [
      { id: 'health', labelKey: 'objectStorage.nav.groups.health', descriptionKey: 'objectStorage.nav.groupDescriptions.health', icon: 'activity', order: 10 },
      { id: 'storage', labelKey: 'objectStorage.nav.groups.storage', descriptionKey: 'objectStorage.nav.groupDescriptions.storage', icon: 'layers', order: 20 },
      { id: 'configuration', labelKey: 'objectStorage.nav.groups.configuration', descriptionKey: 'objectStorage.nav.groupDescriptions.configuration', icon: 'settings', order: 30 },
    ],
    navigation: pages.map(([id, route, labelKey, , groupId, icon], order) => ({
      id, route, labelKey, groupId, icon,
      descriptionKey: `objectStorage.nav.descriptions.${id === 'policy-validation' ? 'validatePolicy' : id}`,
      order,
    })),
    routes: [
      ...pages.map(([id, path, , kind], order) => ({
        id,
        order,
        route: { path, name: `object-storage-${id}`, component: AdminPage, meta: { kind } },
      })),
      { id: 'policy-detail', order: 30, route: { path: '/admin/storage-policies/:id', name: 'object-storage-policy-detail', component: AdminPage, meta: { kind: 'policy-detail' } } },
      { id: 'device-detail', order: 31, route: { path: '/admin/storage-devices/:id', name: 'object-storage-device-detail', component: AdminPage, meta: { kind: 'device-detail' } } },
      { id: 'disk-set-detail', order: 32, route: { path: '/admin/disk-sets/:id', name: 'object-storage-disk-set-detail', component: AdminPage, meta: { kind: 'disk-set-detail' } } },
      { id: 'capacity-detail', order: 33, route: { path: '/admin/capacity/:bucket', name: 'object-storage-capacity-detail', component: AdminPage, meta: { kind: 'capacity-detail' } } },
    ],
    localization: [
      { locale: 'en', namespace: 'objectStorage', messages },
      { locale: 'de', namespace: 'objectStorage', messages },
      { locale: 'es', namespace: 'objectStorage', messages },
      { locale: 'it', namespace: 'objectStorage', messages },
      { locale: 'zh-CN', namespace: 'objectStorage', messages },
    ],
  }
}
