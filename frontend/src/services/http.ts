import { tokenStorage } from '@/services/tokenStorage'
import { refreshAccessToken } from '@/services/tokenRefresh'
import { isTokenExpired } from '@/utils/jwt'

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

/**
 * fetch() con el access token puesto y renovacion automatica. Devuelve la
 * Response tal cual (sin leer el cuerpo), para que sirva tanto a
 * apiRequest() (JSON) como a aiChatApi (streaming SSE).
 */
export async function authorizedFetch(path: string, options: RequestInit = {}): Promise<Response> {
  let token = tokenStorage.getToken()

  // Refresco PROACTIVO: si ya sabemos que el access token ha caducado
  // (leyendo su "exp", ver utils/jwt.ts), lo renovamos antes de enviar la
  // peticion - nos ahorramos un viaje de ida y vuelta que sabemos que va a
  // acabar en 401.
  if (token && isTokenExpired(token)) {
    token = await refreshOrEndSession()
  }

  let response = await send(path, options, token)

  // Refresco REACTIVO: aun asi el backend puede rechazarlo (relojes
  // desincronizados, secreto JWT cambiado en Render...). Renovamos y
  // reintentamos UNA sola vez. Mantenemos el "&& token" del fix anterior:
  // un 401 sin haber enviado token (login con contraseña incorrecta) no es
  // una sesion caducada y no hay nada que renovar.
  if (response.status === 401 && token) {
    token = await refreshOrEndSession()
    // Reenviar es seguro porque nuestros body son siempre strings (JSON):
    // un body de tipo stream solo se puede leer una vez y no se podria
    // reutilizar en el segundo intento.
    response = await send(path, options, token)
  }

  return response
}

export async function apiRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await authorizedFetch(path, options)

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `Error ${response.status} llamando a ${path}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

function send(path: string, options: RequestInit, token: string | null): Promise<Response> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined)
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  // "...options" va ANTES de "headers": en un objeto literal, si dos
  // propiedades se llaman igual gana la ultima. Antes estaba al reves y un
  // options.headers habria pisado entera la cabecera Authorization.
  return fetch(`${BASE_URL}${path}`, { ...options, headers })
}

async function refreshOrEndSession(): Promise<string> {
  const newToken = await refreshAccessToken()
  if (!newToken) {
    // El refresh token tampoco vale (caducado, revocado por logout en otro
    // dispositivo, o por deteccion de reutilizacion): la sesion ha
    // terminado de verdad. Recarga completa a /login a proposito: resetea
    // todo el estado en memoria (Pinia incluido) de un solo golpe.
    tokenStorage.clear()
    window.location.href = '/login'
    throw new Error('Sesion caducada, vuelve a iniciar sesion')
  }
  return newToken
}