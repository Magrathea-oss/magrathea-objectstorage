import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: fileURLToPath(new URL('../../dist/magrathea-example', import.meta.url)),
    emptyOutDir: true,
  },
})
