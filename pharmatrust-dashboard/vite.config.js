import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: '0.0.0.0',  // Bind to all interfaces so mobile on same WiFi can reach it
    proxy: {
      '/api': {
        target: 'http://10.184.81.201:8080',
        changeOrigin: true
      }
    }
  }
})
