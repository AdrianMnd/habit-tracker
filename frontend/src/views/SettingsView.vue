<script setup lang="ts">
import { ref } from 'vue'
import { authApi } from '@/services/authApi'
import { useThemeStore } from '@/stores/themeStore'

const themeStore = useThemeStore()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)
const success = ref(false)

async function handleChangePassword() {
  error.value = null
  success.value = false

  if (newPassword.value !== confirmPassword.value) {
    error.value = 'La nueva contraseña y su confirmación no coinciden'
    return
  }

  submitting.value = true
  try {
    await authApi.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value
    })
    success.value = true
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'No se pudo cambiar la contraseña'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="settings-view">
    <section class="settings-section panel">
      <h2>Apariencia</h2>
      <div class="theme-row">
        <div>
          <p class="theme-label">Tema</p>
          <p class="theme-hint">{{ themeStore.theme === 'dark' ? 'Oscuro' : 'Claro' }}</p>
        </div>
        <label class="switch">
          <input
            type="checkbox"
            class="switch-input"
            :checked="themeStore.theme === 'light'"
            @change="themeStore.setTheme(($event.target as HTMLInputElement).checked ? 'light' : 'dark')"
          />
          <span class="switch-track">
            <span class="switch-thumb"></span>
          </span>
        </label>
      </div>
    </section>

    <section class="settings-section panel">
      <h2>Cambiar contraseña</h2>

      <form @submit.prevent="handleChangePassword">
        <label>
          Contraseña actual
          <input v-model="currentPassword" type="password" required autocomplete="current-password" />
        </label>

        <label>
          Nueva contraseña
          <input v-model="newPassword" type="password" required minlength="8" autocomplete="new-password" />
        </label>

        <label>
          Confirmar nueva contraseña
          <input v-model="confirmPassword" type="password" required minlength="8" autocomplete="new-password" />
        </label>

        <p v-if="error" class="status-text status-text--error">{{ error }}</p>
        <p v-if="success" class="status-text status-text--success">Contraseña actualizada correctamente.</p>

        <button type="submit" :disabled="submitting">
          {{ submitting ? 'Guardando...' : 'Guardar cambios' }}
        </button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.settings-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 820px;
}

.settings-section h2 {
  font-size: 1.1rem;
  margin-bottom: var(--space-4);
}

.theme-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.theme-label {
  font-size: 0.9rem;
  color: var(--color-ink);
  margin: 0;
}

.theme-hint {
  font-size: 0.8rem;
  color: var(--color-ink-soft);
  margin: var(--space-1) 0 0;
}

.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  flex-shrink: 0;
}

/* El checkbox real: sigue ahi (funcional, accesible con teclado y
   lector de pantalla), pero invisible - es .switch-track quien se ve. */
.switch-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  margin: 0;
  cursor: pointer;
  z-index: 1;
}

.switch-track {
  position: absolute;
  inset: 0;
  background: var(--color-stone);
  /* El relleno (--color-stone) puede quedar muy cerca del fondo del
     panel que lo rodea (--color-paper-raised) en el tema oscuro - el
     contorno define la forma del carril incluso cuando el relleno casi
     no contrasta con lo que hay detras. */
  border: 1px solid var(--color-ink-soft);
  border-radius: 999px;
  transition: background 0.15s ease;
  pointer-events: none;
}

.switch-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  /* Color fijo (no un token de tema) a proposito: la bolita necesita
     destacar SIEMPRE sobre el carril, sea claro u oscuro, sea cual sea
     su color de fondo - un tono semantico como --color-paper-raised
     dependeria de que hubiera contraste con lo que hubiera detras, que
     es justo lo que estaba fallando. La sombra remata el contorno para
     que se note incluso si el carril fuera de un tono muy parecido. */
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
  transition: transform 0.15s ease;
}

/* Combinador "+" (hermano siguiente): selecciona .switch-track solo
   cuando el checkbox justo antes esta marcado. Es lo que permite pintar
   el estado "encendido" sin una sola linea de JavaScript, puro CSS. */
.switch-input:checked + .switch-track {
  background: var(--color-moss);
}

.switch-input:checked + .switch-track .switch-thumb {
  /* 44px de carril - 2px margen a cada lado - 18px de bolita = 22px */
  transform: translateX(22px);
}

.switch-input:focus-visible + .switch-track {
  outline: 2px solid var(--color-moss);
  outline-offset: 2px;
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: 0.85rem;
  color: var(--color-ink-soft);
}

input {
  border: 1px solid var(--color-stone);
  background: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-ink);
  font-size: 0.9rem;
}

.status-text {
  font-size: 0.85rem;
  margin: 0;
}

.status-text--error {
  color: var(--color-danger);
}

.status-text--success {
  color: var(--color-moss);
}

form button[type='submit'] {
  align-self: flex-start;
  border: none;
  background: var(--color-ink);
  color: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-4);
  font-size: 0.9rem;
  font-weight: 500;
}

form button[type='submit']:hover {
  background: var(--color-moss);
}

form button[type='submit']:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
