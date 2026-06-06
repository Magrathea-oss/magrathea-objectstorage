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
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
