<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useCategoryStore } from '@/stores/categoryStore'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const store = useCategoryStore()
const newName = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)

onMounted(() => {
  store.fetchCategories()
})

async function handleCreate() {
  if (!newName.value.trim() || submitting.value) return
  submitting.value = true
  formError.value = null
  try {
    await store.addCategory({ name: newName.value.trim() })
    newName.value = ''
  } catch (e) {
    formError.value = e instanceof Error ? e.message : 'No se pudo crear la categoría'
  } finally {
    submitting.value = false
  }
}

async function handleRemove(id: number) {
  await store.removeCategory(id)
}
</script>

<template>
  <div class="categories-view panel">
    <h2>Categorías</h2>
    <p class="hint">
      Agrupa tus hábitos por área (Salud, Trabajo, Aprendizaje...). Si borras una
      categoría, los hábitos que la tenían simplemente se quedan sin categoría —
      no se eliminan.
    </p>

    <form class="new-category-form" @submit.prevent="handleCreate">
      <input v-model="newName" placeholder="Nueva categoría (p. ej. Salud)" maxlength="50" />
      <button type="submit" :disabled="submitting || !newName.trim()">Añadir</button>
    </form>

    <p v-if="formError" class="status-text status-text--error">{{ formError }}</p>
    <LoadingSpinner v-if="store.loading" label="Cargando categorías..." />
    <p v-else-if="store.error" class="status-text status-text--error">{{ store.error }}</p>

    <div v-else-if="store.categories.length === 0" class="empty-state">
      <p>Aún no tienes ninguna categoría creada.</p>
    </div>

    <ul v-else class="category-list">
      <li v-for="category in store.categories" :key="category.id" class="category-item">
        <span>{{ category.name }}</span>
        <button type="button" class="remove-button" @click="handleRemove(category.id)">Eliminar</button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.categories-view {
  max-width: 820px;
}

.categories-view h2 {
  font-size: 1.3rem;
  margin-bottom: var(--space-2);
}

.hint {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0 0 var(--space-6);
}

.new-category-form {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.new-category-form input {
  flex: 1;
  border: 1px solid var(--color-stone);
  background: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-ink);
  font-size: 0.9rem;
}

.new-category-form button {
  border: none;
  background: var(--color-ink);
  color: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-4);
  font-size: 0.85rem;
  font-weight: 500;
}

.new-category-form button:hover {
  background: var(--color-moss);
}

.new-category-form button:disabled {
  opacity: 0.6;
  cursor: default;
}

.status-text {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
}

.status-text--error {
  color: var(--color-danger);
}

.empty-state {
  border: 1px dashed var(--color-stone);
  border-radius: 8px;
  padding: var(--space-5);
  color: var(--color-ink-soft);
}

.empty-state p {
  margin: 0;
}

.category-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-stone);
  font-size: 0.95rem;
  color: var(--color-ink);
}

.category-item:last-child {
  border-bottom: none;
}

.remove-button {
  border: none;
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 0.8rem;
}

.remove-button:hover {
  color: var(--color-danger);
}
</style>
