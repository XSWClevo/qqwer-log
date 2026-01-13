import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src')
    }
  },
  server: {
    host: '0.0.0.0',  // 允许局域网访问
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://192.168.31.90:8080',  // 改为实际 IP
        changeOrigin: true
      }
    }
  }
})
