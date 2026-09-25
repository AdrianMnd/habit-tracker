// defineConfig se importa de 'vitest/config' (no de 'vite' a secas). Ese
// paquete re-exporta el mismo defineConfig de Vite pero con su firma de
// tipos ampliada para reconocer tambien la clave "test" (configuracion de
// Vitest) en el objeto de configuracion. Con el import desde 'vite',
// TypeScript solo conoce los overloads "puros" de Vite (sin "test"), asi
// que al encontrar la propiedad "test" no encaja en NINGUNO de esos
// overloads y falla con "'test' does not exist in type 'UserConfigExport'".
// Cuando TypeScript no puede resolver el overload de una llamada, deja de
// poder inferir los tipos de todo lo que hay DENTRO del objeto literal
// (incluido el resultado de fileURLToPath/URL un poco mas abajo) y por
// eso aparecia tambien el error, aparentemente no relacionado, sobre
// 'node:url' - no es que falten los tipos de Node, es que el fallo del
// overload de arriba arrastraba el chequeo del resto del archivo.
import { defineConfig } from 'vitest/config'
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
    environment: 'jsdom',
    // Por defecto Vitest busca "**/*.{test,spec}.ts" en todo el
    // proyecto, lo que incluye e2e/*.spec.ts - pero esos archivos usan
    // el "test" y el "test.describe" de @playwright/test, un test
    // runner totalmente distinto con su propio registro global de
    // tests. Vitest los importaba igualmente (coinciden con el patron)
    // e intentaba ejecutar ESE test.describe() fuera del test runner de
    // Playwright, lo que Playwright rechaza en tiempo de ejecucion
    // ("did not expect test.describe() to be called here"). Acotar
    // include a src/ dela todo lo de e2e/ exclusivamente a Playwright.
    include: ['src/**/*.test.ts']
  }
})
