import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],

  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  server: {
    port: 5173,
    // 开发环境把 /api 开头的请求代理到后端，避免本地联调时的跨域问题。
    // 若后端端口不是 8080，改这里的 target 即可。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 前端 baseURL 是 /api，后端接口没有 /api 前缀，这里必须去掉
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // 后端返回的图片地址是 /files/upload/...，不带 /api 前缀，
      // 不代理的话浏览器会去 5173 找图，前端预览就全是裂图。
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
