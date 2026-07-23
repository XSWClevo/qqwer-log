import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [
      vue({
        template: {
          compilerOptions: {
            isCustomElement: tagName => ['vue-advanced-chat', 'emoji-picker'].includes(tagName)
          }
        }
      })
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, './src')
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      // 允许通过公网穿透域名/IP 访问 dev server（vite 默认会拦截非白名单 Host）
      allowedHosts: (env.VITE_ALLOWED_HOSTS || '39.98.82.145').split(','),
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        }
      }
    }
  }
})
