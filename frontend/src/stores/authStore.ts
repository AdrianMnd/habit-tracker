import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/services/authApi'
import { tokenStorage } from '@/services/tokenStorage'
import type { LoginRequest, RegisterRequest } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  // Se inicializan desde localStorage: si recargas la pagina, sigues
  // logueado sin tener que volver a escribir credenciales. A partir de
  // aqui, token es la fuente de verdad reactiva - localStorage solo se
  // usa para persistir entre recargas, nunca se vuelve a leer en caliente.
  const token = ref<string | null>(tokenStorage.getToken())
  const email = ref<string | null>(tokenStorage.getEmail())
  const error = ref<string | null>(null)
  const loading = ref(false)

  // Depende de "token.value" (un ref reactivo), no de leer localStorage
  // directamente - eso es lo que permite que Vue recalcule este computed
  // automaticamente en cuanto token.value cambia tras login/logout.
  const isAuthenticated = computed(() => !!token.value)

  async function login(data: LoginRequest) {
    loading.value = true
    error.value = null
    try {
      const response = await authApi.login(data)
      tokenStorage.save(response.token, response.email)
      token.value = response.token
      email.value = response.email
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
      const response = await authApi.register(data)
      tokenStorage.save(response.token, response.email)
      token.value = response.token
      email.value = response.email
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'No se pudo crear la cuenta'
      throw e
    } finally {
      loading.value = false
    }
  }

  function logout() {
    tokenStorage.clear()
    token.value = null
    email.value = null
  }

  return { email, error, loading, isAuthenticated, login, register, logout }
})
