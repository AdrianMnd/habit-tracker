import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/services/authApi'
import { tokenStorage } from '@/services/tokenStorage'
import { isTokenExpired } from '@/utils/jwt'
import type { LoginRequest, RegisterRequest } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  // Si el token guardado de una sesion anterior ya esta caducado, lo
  // descartamos aqui mismo, antes de que nada lo use - asi el guard del
  // router ve isAuthenticated en false desde el primerisimo render, sin
  // esperar a que una llamada de red falle para enterarse.
  const storedToken = tokenStorage.getToken()
  const initialToken = storedToken && !isTokenExpired(storedToken) ? storedToken : null
  if (storedToken && !initialToken) {
    tokenStorage.clear()
  }

  // Se inicializan desde localStorage (ya filtrado arriba): si recargas
  // la pagina, sigues logueado sin tener que volver a escribir
  // credenciales. A partir de aqui, token es la fuente de verdad
  // reactiva - localStorage solo se usa para persistir entre recargas,
  // nunca se vuelve a leer en caliente.
  const token = ref<string | null>(initialToken)
  const email = ref<string | null>(initialToken ? tokenStorage.getEmail() : null)
  const error = ref<string | null>(null)
  const loading = ref(false)

  // Depende de "token.value" (un ref reactivo), no de leer localStorage
  // directamente - eso es lo que permite que Vue recalcule este computed
  // automaticamente en cuanto token.value cambia tras login/logout. Ahora
  // ademas comprueba la caducidad en cada evaluacion, no solo si hay
  // "algo" guardado.
  const isAuthenticated = computed(() => !!token.value && !isTokenExpired(token.value))

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
