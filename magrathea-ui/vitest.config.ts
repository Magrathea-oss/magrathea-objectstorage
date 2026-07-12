import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@magrathea/product-shell': fileURLToPath(
        new URL('./packages/product-shell/src/index.ts', import.meta.url),
      ),
      '@magrathea/magrathea-example': fileURLToPath(
        new URL('./packages/magrathea-example/src/index.ts', import.meta.url),
      ),
      '@magrathea/object-storage-extension': fileURLToPath(
        new URL('./packages/object-storage-extension/src/index.ts', import.meta.url),
      ),
    },
  },
  test: {
    environment: 'jsdom',
    projects: [
      {
        plugins: [vue()],
        resolve: {
          alias: {
            '@magrathea/product-shell': fileURLToPath(
              new URL('./packages/product-shell/src/index.ts', import.meta.url),
            ),
            '@magrathea/magrathea-example': fileURLToPath(
              new URL('./packages/magrathea-example/src/index.ts', import.meta.url),
            ),
            '@magrathea/object-storage-extension': fileURLToPath(
              new URL('./packages/object-storage-extension/src/index.ts', import.meta.url),
            ),
          },
        },
        test: {
          name: 'component',
          environment: 'jsdom',
          include: ['packages/**/*.test.ts', 'apps/**/*.test.ts'],
        },
      },
      {
        plugins: [vue()],
        resolve: {
          alias: {
            '@magrathea/product-shell': fileURLToPath(
              new URL('./packages/product-shell/src/index.ts', import.meta.url),
            ),
          },
        },
        test: {
          name: 'accessibility',
          environment: 'jsdom',
          include: ['templates/**/*.accessibility.test.ts'],
        },
      },
    ],
  },
})
