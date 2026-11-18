import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    cors: false,
    proxy: {
      // Keep the browser's Host header for same-origin development requests.
      // Future API clients should use relative /api URLs and the backend's CSRF flow.
      '/api': {
        target: 'http://127.0.0.1:8080',
      },
    },
  },
})
