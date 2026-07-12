import { createApp, h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { magratheaExampleExtension } from '@magrathea/magrathea-example'
import App from './App.vue'
import '@magrathea/product-shell/theme.css'

const routes = (magratheaExampleExtension.routes ?? []).map(({ route }) => ({
  path: route.path,
  name: route.name,
  meta: route.meta,
  component: { render: () => h('p', 'Example product status is available.') },
}))
const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })

createApp(App).use(router).mount('#app')
