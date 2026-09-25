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
    changePassword: vi.fn()
  }
}))

import { authApi } from '@/services/authApi'

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(authApi.login).mockReset()
  })

  it('isAuthenticated pasa a true tras un login exitoso sin recargar la pagina', async () => {
    // Este es el test de regresion del bug documentado: isAuthenticated
    // era un computed que leia localStorage.getItem() directamente, una
    // fuente NO reactiva para Vue, asi que nunca se recalculaba tras el
    // login. Si alguien reintroduce ese patron, este test debe fallar.
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)

    vi.mocked(authApi.login).mockResolvedValue({ token: makeToken(3600), email: 'adrian@example.com' })

    await store.login({ email: 'adrian@example.com', password: 'secret123' })

    expect(store.isAuthenticated).toBe(true)
    expect(store.email).toBe('adrian@example.com')
  })

  it('un token expirado guardado de una sesion anterior no autentica al arrancar', () => {
    tokenStorage.save(makeToken(-60), 'adrian@example.com')

    const store = useAuthStore()

    expect(store.isAuthenticated).toBe(false)
    // Ademas de no autenticar, el token caducado se descarta de
    // localStorage - si no, cada recarga repetiria el mismo chequeo.
    expect(tokenStorage.getToken()).toBeNull()
  })

  it('logout limpia el estado en memoria y el localStorage', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockResolvedValue({ token: makeToken(3600), email: 'adrian@example.com' })
    await store.login({ email: 'adrian@example.com', password: 'secret123' })
    expect(store.isAuthenticated).toBe(true)

    store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(store.email).toBeNull()
    expect(tokenStorage.getToken()).toBeNull()
  })

  it('un login fallido deja error relleno y no autentica', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockRejectedValue(new Error('Email o contraseña incorrectos'))

    await expect(store.login({ email: 'adrian@example.com', password: 'mala' })).rejects.toThrow()

    expect(store.isAuthenticated).toBe(false)
    expect(store.error).toBe('Email o contraseña incorrectos')
  })
})
