import { tokenStorage } from '@/services/tokenStorage'

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

export async function apiRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const token = tokenStorage.getToken()

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { ...headers, ...(options?.headers as Record<string, string>) },
    ...options
  })

  if (response.status === 401) {
    // El token ha caducado o no es valido: no tiene sentido seguir
    // "logueado" en el frontend si el backend ya no reconoce el token.
    tokenStorage.clear()
    window.location.href = '/login'
    throw new Error('Sesion caducada, vuelve a iniciar sesion')
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `Error ${response.status} llamando a ${path}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}
