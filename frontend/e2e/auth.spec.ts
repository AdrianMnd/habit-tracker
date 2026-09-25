import { test, expect } from '@playwright/test'

/**
 * Estos tests hablan con el backend REAL (Spring Boot + Postgres), no con
 * mocks - por eso cada test registra su propio usuario con un email unico
 * (Date.now() + un contador): si dos tests reutilizaran el mismo email,
 * el segundo fallaria con 409 (EmailAlreadyInUseException) o arrastraria
 * datos del primero. Requiere el backend arrancado en localhost:8080 (ver
 * README - "Tests end-to-end").
 */
function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 10000)}@example.com`
}

test.describe('Autenticación', () => {
  test('registro crea la cuenta y entra directamente a la app', async ({ page }) => {
    const email = uniqueEmail('registro')

    await page.goto('/register')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Contraseña').fill('password123')
    await page.getByRole('button', { name: 'Crear cuenta' }).click()

    // Tras un registro correcto, RegisterView redirige a "/" - la
    // aparicion del sidebar (solo visible con authStore.isAuthenticated)
    // es la prueba real de que la sesion quedo activa, no solo de que la
    // URL cambio.
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: 'Mis hábitos' })).toBeVisible()
    await expect(page.getByText(email)).toBeVisible()
  })

  test('login con contraseña incorrecta muestra el error y no entra', async ({ page }) => {
    const email = uniqueEmail('login-malo')

    // Necesitamos una cuenta real ya creada para poder fallar el login
    // "por contraseña", en vez de por email inexistente.
    await page.goto('/register')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Contraseña').fill('password123')
    await page.getByRole('button', { name: 'Crear cuenta' }).click()
    await expect(page).toHaveURL('/')

    await page.getByRole('button', { name: 'Cerrar sesión' }).click()
    await expect(page).toHaveURL('/login')

    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Contraseña').fill('contraseña-incorrecta')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByText('Email o contraseña incorrectos')).toBeVisible()
    await expect(page).toHaveURL('/login')
  })

  test('una ruta protegida redirige a login si no hay sesión', async ({ page }) => {
    // Test directo del guard de rutas (router/index.ts) - sin login
    // previo, navegar a /progress debe acabar en /login, no en un 401 en
    // consola con la pagina vacia detras.
    await page.goto('/progress')
    await expect(page).toHaveURL('/login')
  })

  test('logout limpia la sesión y bloquea de nuevo las rutas protegidas', async ({ page }) => {
    const email = uniqueEmail('logout')
    await page.goto('/register')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Contraseña').fill('password123')
    await page.getByRole('button', { name: 'Crear cuenta' }).click()
    await expect(page).toHaveURL('/')

    await page.getByRole('button', { name: 'Cerrar sesión' }).click()
    await expect(page).toHaveURL('/login')

    await page.goto('/')
    await expect(page).toHaveURL('/login')
  })
})
