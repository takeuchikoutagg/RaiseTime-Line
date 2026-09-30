import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Taskmanagement（5173）と重ならないポート。使用中なら別のポートに逃がさずエラーにする
    port: 5174,
    strictPort: true,
    // /api はバックエンド（8081）へ転送する。画面と API が同じオリジンになるので CORS 不要・Cookie もそのまま使える
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
  },
})
