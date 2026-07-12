import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: fileURLToPath(new URL('../../dist/object-storage', import.meta.url)),
    emptyOutDir: true,
  },
  preview: {
    proxy: {},
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/admin': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
