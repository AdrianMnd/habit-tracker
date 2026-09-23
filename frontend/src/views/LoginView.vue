<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const router = useRouter()

const email = ref('')
const password = ref('')

async function handleSubmit() {
  try {
    await authStore.login({ email: email.value, password: password.value })
    router.push('/')
  } catch {
    // el error ya queda expuesto en authStore.error
  }
}
</script>

<template>
  <div class="auth-view">
    <form class="auth-form panel" @submit.prevent="handleSubmit">
      <h2>Iniciar sesión</h2>

      <label>
        Email
        <input v-model="email" type="email" required autocomplete="email" />
      </label>

      <label>
        Contraseña
        <input v-model="password" type="password" required autocomplete="current-password" />
      </label>

      <p v-if="authStore.error" class="auth-error">{{ authStore.error }}</p>

      <button type="submit" :disabled="authStore.loading">
        {{ authStore.loading ? 'Entrando...' : 'Entrar' }}
      </button>

      <p class="auth-switch">
        ¿No tienes cuenta? <RouterLink to="/register">Regístrate</RouterLink>
      </p>
    </form>
  </div>
</template>

<style scoped>
.auth-view {
  display: flex;
  justify-content: center;
  padding-top: var(--space-12);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 100%;
  max-width: 340px;
}

.auth-form h2 {
  margin-bottom: var(--space-2);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: 0.9rem;
  color: var(--color-ink-soft);
}

input {
  border: 1px solid var(--color-stone);
  background: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-ink);
  font-size: 0.95rem;
}

button {
  border: none;
  background: var(--color-ink);
  color: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-3);
  font-size: 0.95rem;
  font-weight: 500;
}

button:hover {
  background: var(--color-moss);
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}

.auth-error {
  color: var(--color-danger);
  font-size: 0.85rem;
  margin: 0;
}

.auth-switch {
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0;
}
</style>
