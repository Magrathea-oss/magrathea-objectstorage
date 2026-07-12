<script setup lang="ts">
import { ProductShell } from '@magrathea/product-shell'
import { magratheaExampleExtension } from '@magrathea/magrathea-example'

const navigation = magratheaExampleExtension.navigation ?? []
</script>

<template>
  <ProductShell
    page-title="Example status"
    page-description="Product-neutral shell composition fixture."
    :navigation="navigation"
    active-route="/example/status"
  >
    <template #navigation-label>Example</template>
    <router-view />
  </ProductShell>
</template>
