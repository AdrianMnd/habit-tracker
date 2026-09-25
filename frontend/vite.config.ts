import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    // Los stores usan APIs de navegador (localStorage en tokenStorage.ts)
    // que no existen en Node "a secas" - jsdom simula un DOM/window
    // minimo para que ese codigo se ejecute igual que en el navegador
    // real, sin necesidad de un navegador de verdad.
    environment: 'jsdom'
  }
})
