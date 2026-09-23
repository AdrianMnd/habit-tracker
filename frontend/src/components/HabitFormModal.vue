<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import { useCategoryStore } from '@/stores/categoryStore'
import type { Habit, HabitPriority } from '@/types/habit'

// Si "habit" viene informado, la modal entra en modo edicion; si no,
// modo creacion. Un unico componente para los dos casos, en vez de
// duplicar practicamente el mismo formulario dos veces.
const props = defineProps<{
  habit?: Habit | null
}>()

const emit = defineEmits<{ close: [] }>()

const habitStore = useHabitStore()
const categoryStore = useCategoryStore()

const isEditMode = computed(() => !!props.habit)

const name = ref(props.habit?.name ?? '')
const description = ref(props.habit?.description ?? '')
const priority = ref<HabitPriority>(props.habit?.priority ?? 'MEDIA')
const categoryId = ref<number | ''>(props.habit?.category?.id ?? '')
const submitting = ref(false)
const error = ref<string | null>(null)

onMounted(() => {
  if (categoryStore.categories.length === 0) {
    categoryStore.fetchCategories()
  }
})

async function handleSubmit() {
  if (!name.value.trim() || submitting.value) return

  submitting.value = true
  error.value = null

  const payload = {
    name: name.value.trim(),
    description: description.value.trim() || undefined,
    priority: priority.value,
    categoryId: categoryId.value || undefined
  }

  try {
    if (isEditMode.value && props.habit) {
      await habitStore.updateHabit(props.habit.id, payload)
    } else {
      await habitStore.addHabit(payload)
    }
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'No se pudo guardar el hábito'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="emit('close')">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="habit-form-title">
        <div class="modal-header">
          <h3 id="habit-form-title">{{ isEditMode ? 'Editar hábito' : 'Nuevo hábito' }}</h3>
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

          <label>
            Categoría <span class="optional">(opcional)</span>
            <select v-model="categoryId">
              <option value="">Sin categoría</option>
              <option v-for="category in categoryStore.categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <p v-if="error" class="modal-error">{{ error }}</p>

          <div class="modal-actions">
            <button type="button" class="secondary" @click="emit('close')">Cancelar</button>
            <button type="submit" :disabled="submitting || !name.trim()">
              <template v-if="isEditMode">{{ submitting ? 'Guardando...' : 'Guardar cambios' }}</template>
              <template v-else>{{ submitting ? 'Creando...' : 'Añadir' }}</template>
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
