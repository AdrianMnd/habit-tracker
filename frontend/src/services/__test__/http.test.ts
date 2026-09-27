import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/services/http'
import { refreshAccessToken } from '@/services/tokenRefresh'
import { tokenStorage } from '@/services/tokenStorage'
import { makeToken } from '@/test-utils/jwt'

// Construye una Response real (la API Response existe en Node 18+ y en
// jsdom), para no tener que imitar a mano su forma.
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

describe('renovacion del access token', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    localStorage.clear()
    fetchMock.mockReset()
    // Sustituye el fetch global solo durante este archivo de tests.
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('varias renovaciones simultaneas comparten un unico /refresh (single-flight)', async () => {
    tokenStorage.save(makeToken(-60), 'refresh-viejo', 'adrian@example.com')
    const nuevoAccess = makeToken(900)
    fetchMock.mockResolvedValue(
      jsonResponse(200, { token: nuevoAccess, refreshToken: 'refresh-nuevo', email: 'adrian@example.com' })
    )

    // Promise.all lanza las tres a la vez, como hace la vista principal al
    // pedir habitos, resumen y categorias en paralelo.
    const results = await Promise.all([refreshAccessToken(), refreshAccessToken(), refreshAccessToken()])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(results).toEqual([nuevoAccess, nuevoAccess, nuevoAccess])
    expect(tokenStorage.getRefreshToken()).toBe('refresh-nuevo')
  })

  it('ante un 401 renueva el access token y reintenta la peticion una vez', async () => {
    const accessViejo = makeToken(900) // aun no caducado segun su "exp"...
    const accessNuevo = makeToken(900)
    tokenStorage.save(accessViejo, 'refresh-viejo', 'adrian@example.com')

    fetchMock
      // ...pero el backend lo rechaza igualmente (p. ej. secreto JWT rotado)
      .mockResolvedValueOnce(jsonResponse(401, { message: 'Token ausente, invalido o caducado' }))
      .mockResolvedValueOnce(
        jsonResponse(200, { token: accessNuevo, refreshToken: 'refresh-nuevo', email: 'adrian@example.com' })
      )
      .mockResolvedValueOnce(jsonResponse(200, [{ id: 1, name: 'Meditar' }]))

    const habits = await apiRequest<{ id: number; name: string }[]>('/habits')

    expect(habits).toEqual([{ id: 1, name: 'Meditar' }])
    expect(fetchMock).toHaveBeenCalledTimes(3)
    // La tercera llamada (el reintento) lleva ya el access token nuevo.
    const [, reintentoOptions] = fetchMock.mock.calls[2]
    expect(reintentoOptions.headers.Authorization).toBe(`Bearer ${accessNuevo}`)
  })

  it('un 401 sin haber enviado token (login fallido) no intenta renovar nada', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { message: 'Email o contraseña incorrectos' }))

    await expect(apiRequest('/auth/login', { method: 'POST', body: '{}' })).rejects.toThrow(
      'Email o contraseña incorrectos'
    )
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})