import { createRouter, createWebHistory } from 'vue-router'
import { createObjectStorageExtension } from '@magrathea/object-storage-extension'
import DocsViewer from './components/DocsViewer.vue'

const extension = createObjectStorageExtension()
const extensionRoutes = (extension.routes || []).map((registration) => registration.route)

export const routes = [
  ...extensionRoutes,
  { path: '/admin/docs', name: 'documentation', component: DocsViewer, meta: { kind: 'documentation' } },
  { path: '/admin/docs/arc42', name: 'docs-arc42', component: DocsViewer, props: { initialDocType: 'arc42' }, meta: { kind: 'documentation' } },
  { path: '/admin/docs/test-report', name: 'docs-test-report', component: DocsViewer, props: { initialDocType: 'testreport' }, meta: { kind: 'documentation' } },
  { path: '/admin/docs/adr/:id', name: 'docs-adr', component: DocsViewer, props: { initialDocType: 'adr' }, meta: { kind: 'documentation' } },
  { path: '/', redirect: '/admin' },
  { path: '/:pathMatch(.*)*', redirect: '/admin' },
]

export default createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })
