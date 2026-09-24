import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/ERP/resources/app/',
  build: {
    outDir: fileURLToPath(new URL('../src/main/resources/static/resources/app', import.meta.url)),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: 'app.js',
        chunkFileNames: 'chunks/[name].js',
        assetFileNames: ({ names }) => names?.some((name) => name.endsWith('.css'))
          ? 'app.css'
          : 'assets/[name][extname]'
      }
    }
  }
})
