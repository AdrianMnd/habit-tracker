import { tokenStorage } from '@/services/tokenStorage'
import type { AuthResponse } from '@/types/auth'

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

// La promesa del refresco que esta en curso ahora mismo (o null si no hay
// ninguno). Vive a nivel de modulo: un modulo ES se evalua una sola vez, asi
// que esta variable es compartida por todo el que importe este archivo -
// el equivalente a un campo "static" de una clase en Java.
let refreshInFlight: Promise<string | null> | null = null

/**
 * Pide un access token nuevo usando el refresh token guardado. Devuelve el
 * access token nuevo, o null si la sesion ya no se puede renovar.
 *
 * "Single-flight": si varias peticiones descubren a la vez que el access
 * token ha caducado (la vista principal lanza habitos + resumen + categorias
 * en paralelo nada mas cargar), todas reciben LA MISMA promesa en vez de
 * lanzar cada una su propio /refresh. Esto no es solo una optimizacion: con
 * rotacion, el segundo /refresh llegaria con un refresh token que el primero
 * ya consumio, el backend lo interpretaria como una reutilizacion (posible
 * robo) y revocaria todas las sesiones del usuario.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    // .finally() libera el "cerrojo" tanto si sale bien como si falla,
    // para que el siguiente refresco (dentro de 15 min) vuelva a llamar
    // al backend en vez de reutilizar esta promesa ya resuelta.
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) return null

  try {
    // fetch() directo y NO apiRequest()/authorizedFetch(): esos reintentan
    // con un refresco cuando reciben un 401, y un /refresh rechazado
    // devuelve precisamente 401 - pasar por ahi acabaria esperando a su
    // propia promesa (refreshInFlight) y no terminaria nunca.
    const response = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    })
    if (!response.ok) return null

    const data = (await response.json()) as AuthResponse
    // Guardamos el refresh token NUEVO: el anterior ya no vale (rotacion).
    tokenStorage.save(data.token, data.refreshToken, data.email)
    return data.token
  } catch {
    return null
  }
}