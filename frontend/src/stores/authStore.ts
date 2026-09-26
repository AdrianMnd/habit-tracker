import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/services/authApi'
import { tokenStorage } from '@/services/tokenStorage'
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  // La sesion la define el REFRESH token, no el access token. El access
  // token caduca cada 15 minutos por diseño, y eso ya NO significa "sesion
  // terminada": http.ts lo renueva solo, sin que el usuario se entere. Si
  // el store siguiera mirando la caducidad del access token (como antes),
  // el guard del router mandaria a /login a alguien con una sesion
  // perfectamente valida en cuanto pasaran 15 min sin recargar.
  const hasSession = ref(!!tokenStorage.getRefreshToken())

  // Restos de una sesion anterior a los refresh tokens (solo access token
  // guardado): no se pueden renovar, asi que se limpian y toca un login.
  if (!hasSession.value) {
    tokenStorage.clear()
  }

  const email = ref<string | null>(hasSession.value ? tokenStorage.getEmail() : null)
  const error = ref<string | null>(null)
  const loading = ref(false)

  // Sigue dependiendo de un ref reactivo (hasSession), nunca de leer
  // localStorage en caliente - ese fue el bug original de este store.
  // computed() y no exponer hasSession directamente: desde fuera es de
  // solo lectura, nadie puede hacer "authStore.isAuthenticated = true".
  const isAuthenticated = computed(() => hasSession.value)

  function startSession(response: AuthResponse) {
    tokenStorage.save(response.token, response.refreshToken, response.email)
    hasSession.value = true
    email.value = response.email
  }

  async function login(data: LoginRequest) {
    loading.value = true
    error.value = null
    try {
      startSession(await authApi.login(data))
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'No se pudo iniciar sesion'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function register(data: RegisterRequest) {
    loading.value = true
    error.value = null
    try {
      startSession(await authApi.register(data))
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'No se pudo crear la cuenta'
      throw e
    } finally {
      loading.value = false
    }
  }

  function logout() {
    const refreshToken = tokenStorage.getRefreshToken()

    // Primero limpiamos el estado local, sin esperar al backend: el
    // usuario ha pulsado "Cerrar sesion" y la UI debe reaccionar ya.
    tokenStorage.clear()
    hasSession.value = false
    email.value = null

    if (refreshToken) {
      // "Fire and forget": lanzamos la revocacion en el servidor sin await.
      // Si falla (backend dormido en Render, sin red...), el usuario ya
      // esta deslogueado en este navegador y el token caducara solo a los
      // 7 dias - no merece la pena bloquear ni mostrar un error por ello.
      // El .catch() vacio evita un "unhandled promise rejection" en consola.
      authApi.logout(refreshToken).catch(() => {})
    }
  }

  return { email, error, loading, isAuthenticated, login, register, logout }
})