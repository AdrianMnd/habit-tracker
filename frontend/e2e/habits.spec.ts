import { test, expect, Page } from '@playwright/test'

function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 10000)}@example.com`
}

// No hay backend "de pega" con datos de prueba precargados - cada test
// necesita su propio usuario limpio (sin habitos de otros tests) para que
// las aserciones sobre "Mis habitos" no dependan del orden de ejecucion.
// Como los tests corren en serie (workers: 1, ver playwright.config.ts),
// esto no penaliza el tiempo total de forma relevante.
async function registerAndLogin(page: Page): Promise<string> {
  const email = uniqueEmail('habitos')
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Contraseña').fill('password123')
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page.getByRole('heading', { name: 'Mis hábitos' })).toBeVisible()
  return email
}

test.describe('Gestión de hábitos', () => {
  test.beforeEach(async ({ page }) => {
    await registerAndLogin(page)
  })

  test('crear un hábito lo añade a la lista', async ({ page }) => {
    await page.getByRole('button', { name: '+ Añadir hábito' }).click()

    await page.getByLabel('Nombre').fill('Meditar 10 minutos')
    await page.getByLabel('Descripción').fill('Justo al despertar')
    await page.getByRole('button', { name: 'Añadir', exact: true }).click()

    // La modal se cierra sola (emit('close') tras el await de addHabit) -
    // si el POST fallase, error.value quedaria relleno y la modal seguiria
    // abierta con el mensaje, así que "la modal desaparece y el hábito se
    // ve en la lista" es prueba de que el flujo completo (petición +
    // actualización del store) funcionó.
    await expect(page.getByRole('dialog')).not.toBeVisible()
    await expect(page.getByText('Meditar 10 minutos')).toBeVisible()
    await expect(page.getByText('Justo al despertar')).toBeVisible()
  })

  test('marcar un hábito como hecho hoy actualiza el botón', async ({ page }) => {
    await page.getByRole('button', { name: '+ Añadir hábito' }).click()
    await page.getByLabel('Nombre').fill('Beber agua')
    await page.getByRole('button', { name: 'Añadir', exact: true }).click()
    await expect(page.getByText('Beber agua')).toBeVisible()

    const marcarButton = page.getByRole('button', { name: 'Marcar hoy' })
    await marcarButton.click()

    // El texto del boton cambia de "Marcar hoy" a "Hecho hoy ✓" solo
    // cuando POST /habits/{id}/logs responde bien (ver HabitCard.vue,
    // handleToggle) - no es un simple toggle optimista en el frontend.
    await expect(page.getByRole('button', { name: 'Hecho hoy ✓' })).toBeVisible()
  })

  test('archivar un hábito lo mueve de "Mis hábitos" a "Archivo"', async ({ page }) => {
    await page.getByRole('button', { name: '+ Añadir hábito' }).click()
    await page.getByLabel('Nombre').fill('Hábito temporal')
    await page.getByRole('button', { name: 'Añadir', exact: true }).click()
    await expect(page.getByText('Hábito temporal')).toBeVisible()

    await page.getByRole('button', { name: 'Archivar' }).click()
    await expect(page.getByText('Hábito temporal')).not.toBeVisible()

    await page.getByRole('link', { name: 'Archivo' }).click()
    await expect(page).toHaveURL('/archive')
    await expect(page.getByText('Hábito temporal')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Restaurar' })).toBeVisible()
  })

  test('el chat de IA queda visible junto al listado de hábitos', async ({ page }) => {
    // No probamos el streaming de Gemini en si (requeriria una
    // GEMINI_API_KEY real y no es determinista) - solo que el panel
    // global (App.vue) esta presente y operativo una vez autenticado,
    // que es justo lo que se rompio con el bug de layout de esta semana.
    await expect(page.getByRole('heading', { name: 'Consulta a la IA' })).toBeVisible()
    await expect(page.getByPlaceholder('Escribe tu mensaje...')).toBeEnabled()
  })
})
