import type { ProductExtension } from '@magrathea/product-shell'
import AdminPage from './AdminPage.vue'

const pages = [
  ['dashboard', '/admin', 'objectStorage.nav.dashboard', 'dashboard'],
  ['backend', '/admin/backend-status', 'objectStorage.nav.backend', 'backend'],
  ['policies', '/admin/storage-policies', 'objectStorage.nav.policies', 'policies'],
  ['policy-validation', '/admin/storage-policies/validate', 'objectStorage.nav.validatePolicy', 'policy-validation'],
  ['devices', '/admin/storage-devices', 'objectStorage.nav.devices', 'devices'],
  ['disk-sets', '/admin/disk-sets', 'objectStorage.nav.diskSets', 'disk-sets'],
  ['capacity', '/admin/capacity', 'objectStorage.nav.capacity', 'capacity'],
  ['data-hygiene', '/admin/data-hygiene', 'objectStorage.nav.dataHygiene', 'data-hygiene'],
  ['observability', '/admin/observability', 'objectStorage.nav.observability', 'observability'],
  ['diagnostics', '/admin/s3-diagnostics', 'objectStorage.nav.diagnostics', 'diagnostics'],
] as const

const messages = {
  nav: {
    dashboard: 'Dashboard', backend: 'Backend status', policies: 'Storage policies',
    validatePolicy: 'Validate policy', devices: 'Storage devices', diskSets: 'Disk-set topology',
    capacity: 'Bucket capacity', dataHygiene: 'Data hygiene', observability: 'Observability',
    diagnostics: 'S3 diagnostics', documentation: 'Documentation',
  },
}

export const objectStorageEnglishMessages = Object.freeze(messages)

export function createObjectStorageExtension(): ProductExtension {
  return {
    id: 'object-storage',
    order: 10,
    capabilities: [{ id: 'object-storage-admin', description: 'Object Storage Admin Control Plane' }],
    navigation: pages.map(([id, route, labelKey], order) => ({ id, route, labelKey, order })),
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
