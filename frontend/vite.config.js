import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev: mọi request /api/** được chuyển tới backend Spring Boot (mặc định cổng 5080)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:5080',
        changeOrigin: true,
      },
    },
  },
});
