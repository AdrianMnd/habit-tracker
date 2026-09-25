import { defineConfig, devices } from '@playwright/test'

/**
 * webServer arranca automaticamente el frontend (npm run dev) antes de los
 * tests y lo apaga al terminar - Playwright espera a que responda en
 * baseURL antes de empezar. El backend NO se arranca aqui: a diferencia
 * del frontend (que no depende de nada mas), el backend necesita Postgres
 * arriba y variables de entorno propias (JWT_SECRET, etc.), asi que se
 * deja como prerrequisito explicito documentado en el README en vez de
 * intentar orquestarlo tambien desde este config.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  // Los tests e2e escriben datos reales (usuarios, habitos) contra el
  // backend/BD real - a diferencia de un test unitario con mocks, no son
  // independientes entre si si compartiesen estado, asi que van en serie
  // y cada uno crea su propio usuario para no pisarse entre ellos.
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000
  }
})
