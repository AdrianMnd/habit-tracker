<script setup lang="ts">
import { ref } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import type { HabitPriority } from '@/types/habit'

const emit = defineEmits<{ close: [] }>()

const habitStore = useHabitStore()

const name = ref('')
const description = ref('')
const priority = ref<HabitPriority>('MEDIA')
const submitting = ref(false)
const error = ref<string | null>(null)

async function handleSubmit() {
  if (!name.value.trim() || submitting.value) return

  submitting.value = true
  error.value = null

  try {
    await habitStore.addHabit({
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      priority: priority.value
    })
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'No se pudo crear el hábito'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="emit('close')">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="add-habit-title">
        <div class="modal-header">
          <h3 id="add-habit-title">Nuevo hábito</h3>
          <button type="button" class="close-button" aria-label="Cerrar" @click="emit('close')">✕</button>
        </div>

        <form @submit.prevent="handleSubmit">
          <label>
            Nombre
            <input v-model="name" required autofocus placeholder="p. ej. Leer 20 min" />
          </label>

          <label>
            Descripción <span class="optional">(opcional)</span>
            <textarea v-model="description" rows="2" placeholder="Detalles adicionales..."></textarea>
          </label>

          <label>
            Prioridad
            <select v-model="priority">
              <option value="BAJA">Baja</option>
              <option value="MEDIA">Media</option>
              <option value="ALTA">Alta</option>
            </select>
          </label>

          <p v-if="error" class="modal-error">{{ error }}</p>

          <div class="modal-actions">
            <button type="button" class="secondary" @click="emit('close')">Cancelar</button>
            <button type="submit" :disabled="submitting || !name.trim()">
              {{ submitting ? 'Creando...' : 'Añadir' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  /* Sin este padding, en una pantalla mas estrecha que 380px (el
     max-width de la modal) el "width: 100%" de .modal tocaria los bordes
     del todo, pegado al cristal - se ve peor y es mas incomodo de tocar. */
  padding: var(--space-4);
  z-index: 100;
}

.modal {
  background: var(--color-paper-raised);
  border: 1px solid var(--color-stone);
  border-radius: 10px;
  padding: var(--space-6);
  width: 100%;
  max-width: 380px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
}

.modal-header h3 {
  margin: 0;
}

.close-button {
  border: none;
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 1rem;
  line-height: 1;
  padding: var(--space-1);
}

.close-button:hover {
  color: var(--color-danger);
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

.optional {
  font-weight: 400;
  color: var(--color-ink-soft);
}

input,
textarea,
select {
  border: 1px solid var(--color-stone);
  background: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-ink);
  font-size: 0.9rem;
  font-family: inherit;
  resize: vertical;
}

.modal-error {
  color: var(--color-danger);
  font-size: 0.85rem;
  margin: 0;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.modal-actions button {
  border: none;
  border-radius: 6px;
  padding: var(--space-2) var(--space-4);
  font-size: 0.9rem;
  font-weight: 500;
}

.modal-actions button[type='submit'] {
  background: var(--color-ink);
  color: var(--color-paper);
}

.modal-actions button[type='submit']:hover {
  background: var(--color-moss);
}

.modal-actions button[type='submit']:disabled {
  opacity: 0.6;
  cursor: default;
}

.modal-actions .secondary {
  background: transparent;
  color: var(--color-ink-soft);
}
</style>
