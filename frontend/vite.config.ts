import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev: the SPA runs on :5173 and proxies /api to the Spring Boot backend on :8080 (same-origin in prod).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  test: {
    environment: 'node',
  },
} as any)
