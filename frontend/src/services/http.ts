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

  // Ojo: "token" (no solo response.status === 401) - un 401 en
  // /auth/login por contraseña incorrecta tambien es un 401, pero ahi NO
  // habia sesion que haya caducado (no se envio ningun Bearer token en
  // absoluto). Sin este chequeo, un login fallido se trataba igual que
  // un token caducado: se forzaba un window.location.href (recarga dura
  // de pagina, no una navegacion de Vue Router) que perdia el mensaje de
  // error real del backend y el estado de authStore.error antes de que
  // LoginView llegara a pintarlo - el usuario solo veia un parpadeo y
  // volvia a la pantalla de login sin explicacion. Lo detectaron los
  // tests e2e: la aserción sobre el mensaje de error nunca encontraba el
  // texto porque la pagina se habia recargado por completo entretanto.
  if (response.status === 401 && token) {
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