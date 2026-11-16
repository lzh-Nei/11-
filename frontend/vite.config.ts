import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// dev 代理：/api → 本地 nginx 网关 8081（compose-dev 端口表 D-13）
// alias 用 process.cwd（经 new URL/fileURLToPath 在中文路径下解析失败，弃用）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(process.cwd(), 'src') }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})

