import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/authStore'
import { tokenStorage } from '@/services/tokenStorage'
import { makeToken } from '@/test-utils/jwt'

// authApi.login/register hacen fetch() contra el backend real - en un
// test unitario no queremos (ni podemos) levantar Spring Boot, asi que
// sustituimos el modulo entero por un doble controlado por nosotros.
// vi.mock se "hoistea" (se mueve) al principio del archivo por Vitest,
// asi que la factory no puede cerrar sobre variables externas salvo que
// esten prefijadas con "mock" (retirccion que vi.mock exige a proposito).
vi.mock('@/services/authApi', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    changePassword: vi.fn()
  }
}))

import { authApi } from '@/services/authApi'

const loginResponse = () => ({
  token: makeToken(900),
  refreshToken: 'refresh-abc',
  email: 'adrian@example.com'
})

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(authApi.login).mockReset()
    vi.mocked(authApi.logout).mockReset().mockResolvedValue(undefined)
  })

  it('isAuthenticated pasa a true tras un login exitoso sin recargar la pagina', async () => {
    // Este es el test de regresion del bug documentado: isAuthenticated
    // era un computed que leia localStorage.getItem() directamente, una
    // fuente NO reactiva para Vue, asi que nunca se recalculaba tras el
    // login. Si alguien reintroduce ese patron, este test debe fallar.
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)

    vi.mocked(authApi.login).mockResolvedValue(loginResponse())

    await store.login({ email: 'adrian@example.com', password: 'secret123' })

    expect(store.isAuthenticated).toBe(true)
    expect(store.email).toBe('adrian@example.com')
    expect(tokenStorage.getRefreshToken()).toBe('refresh-abc')
  })

  it('la sesion sobrevive al arrancar aunque el access token guardado haya caducado', () => {
    // Access token caducado hace un minuto, pero refresh token presente:
    // la sesion sigue viva (http.ts renovara el access token en la
    // primera peticion). Antes de los refresh tokens, esto era un logout.
    tokenStorage.save(makeToken(-60), 'refresh-abc', 'adrian@example.com')

    const store = useAuthStore()

    expect(store.isAuthenticated).toBe(true)
    expect(store.email).toBe('adrian@example.com')
  })

  it('sin refresh token guardado no hay sesion, y se limpian los restos de una sesion antigua', () => {
    // Simula una sesion de antes de este cambio: solo el access token.
    localStorage.setItem('habit-tracker:token', makeToken(3600))
    localStorage.setItem('habit-tracker:email', 'adrian@example.com')

    const store = useAuthStore()

    expect(store.isAuthenticated).toBe(false)
    expect(tokenStorage.getToken()).toBeNull()
  })

  it('logout limpia el estado local y revoca el refresh token en el backend', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockResolvedValue(loginResponse())
    await store.login({ email: 'adrian@example.com', password: 'secret123' })

    store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(store.email).toBeNull()
    expect(tokenStorage.getToken()).toBeNull()
    expect(tokenStorage.getRefreshToken()).toBeNull()
    expect(authApi.logout).toHaveBeenCalledWith('refresh-abc')
  })

  it('un login fallido deja error relleno y no autentica', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockRejectedValue(new Error('Email o contraseña incorrectos'))

    await expect(store.login({ email: 'adrian@example.com', password: 'mala' })).rejects.toThrow()

    expect(store.isAuthenticated).toBe(false)
    expect(store.error).toBe('Email o contraseña incorrectos')
  })
})