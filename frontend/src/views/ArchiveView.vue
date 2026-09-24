<script setup lang="ts">
import { onMounted } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const store = useHabitStore()

onMounted(() => {
  store.fetchArchivedHabits()
})

async function handleRestore(id: number) {
  await store.unarchiveHabit(id)
}

async function handleDeletePermanently(id: number) {
  await store.removeHabit(id)
}
</script>

<template>
  <div class="archive-view panel">
    <h2>Archivo</h2>
    <p class="hint">
      Hábitos que ya no sigues activamente, pero cuyo historial quieres conservar.
      Puedes restaurarlos en cualquier momento, o eliminarlos del todo si ya no los necesitas.
    </p>

    <LoadingSpinner v-if="store.loading" label="Cargando archivo..." />
    <p v-else-if="store.error" class="status-text status-text--error">{{ store.error }}</p>

    <div v-else-if="store.archivedHabits.length === 0" class="empty-state">
      <p>No tienes ningún hábito archivado.</p>
    </div>

    <ul v-else class="archive-list">
      <li v-for="habit in store.archivedHabits" :key="habit.id" class="archive-item">
        <div class="archive-item__info">
          <span class="archive-item__name">{{ habit.name }}</span>
          <span v-if="habit.category" class="archive-item__category">{{ habit.category.name }}</span>
        </div>
        <div class="archive-item__actions">
          <button type="button" class="restore-button" @click="handleRestore(habit.id)">Restaurar</button>
          <button type="button" class="delete-button" @click="handleDeletePermanently(habit.id)">
            Eliminar definitivamente
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.archive-view {
  max-width: 820px;
}

.archive-view h2 {
  font-size: 1.3rem;
  margin-bottom: var(--space-2);
}

.hint {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0 0 var(--space-6);
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

.archive-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.archive-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-stone);
}

.archive-item:last-child {
  border-bottom: none;
}

.archive-item__info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.archive-item__name {
  font-family: var(--font-display);
  font-size: 1rem;
  color: var(--color-ink);
}

.archive-item__category {
  font-size: 0.75rem;
  color: var(--color-ink-soft);
}

.archive-item__actions {
  display: flex;
  gap: var(--space-3);
  flex-shrink: 0;
}

.restore-button,
.delete-button {
  border: none;
  background: transparent;
  font-size: 0.8rem;
  white-space: nowrap;
}

.restore-button {
  color: var(--color-moss);
}

.delete-button {
  color: var(--color-ink-soft);
}

.delete-button:hover {
  color: var(--color-danger);
}
</style>
