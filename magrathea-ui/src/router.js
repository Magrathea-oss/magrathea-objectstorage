import { createRouter, createWebHistory } from 'vue-router'
import StoragePolicies from './components/StoragePolicies.vue'
import DocsViewer from './components/DocsViewer.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('./App.vue'),
  },
  {
    path: '/storage-policies',
    name: 'storage-policies',
    component: StoragePolicies,
  },
  {
    path: '/docs',
    name: 'docs',
    component: DocsViewer,
  },
  {
    path: '/docs/arc42',
    name: 'docs-arc42',
    component: DocsViewer,
    props: { initialDocType: 'arc42' },
  },
  {
    path: '/docs/test-report',
    name: 'docs-test-report',
    component: DocsViewer,
    props: { initialDocType: 'testreport' },
  },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
