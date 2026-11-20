import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // '@' → src 目录：import '@/api/xxx' 不用写 ../../ 相对路径
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发期把 /api 开头的请求代理到后端 8080。
    // 为什么不直接给后端开 CORS：
    // 1. 浏览器看到的请求是同源的（localhost:5173/api/...），没有跨域预检
    // 2. 生产环境 nginx 也是同架构（同域名反代），开发和生产的请求路径写法完全一致
    // 3. 后端零放宽——安全配置不用为前端让步
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
