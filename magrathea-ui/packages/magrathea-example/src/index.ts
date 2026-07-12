import type { Component } from 'vue'
import type { ProductExtension } from '@magrathea/product-shell'

/** Contract-only screen marker; fixture hosts may replace it with their own renderer. */
export const ExampleStatusScreen: Component = Object.freeze({ name: 'ExampleStatusScreen' })

export const magratheaExampleExtension: ProductExtension = Object.freeze({
  id: 'magrathea-example',
  navigation: Object.freeze([
    Object.freeze({
      id: 'example-status',
      labelKey: 'example.navigation.status',
      route: '/example/status',
      permission: 'example:status:read',
    }),
  ]),
  routes: Object.freeze([
    Object.freeze({
      id: 'example-status',
      route: Object.freeze({
        path: '/example/status',
        name: 'example-status',
        component: ExampleStatusScreen,
      }),
    }),
  ]),
  permissions: Object.freeze(['example:status:read']),
  localization: Object.freeze([
    Object.freeze({
      locale: 'en',
      namespace: 'example',
      messages: Object.freeze({ navigation: Object.freeze({ status: 'Example' }) }),
    }),
  ]),
})
