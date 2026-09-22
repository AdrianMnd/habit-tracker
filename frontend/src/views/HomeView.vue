<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import HabitCard from '@/components/HabitCard.vue'
import ChatPanel from '@/components/ChatPanel.vue'

const store = useHabitStore()
const newHabitName = ref('')

onMounted(() => {
  store.fetchHabits()
})

async function handleCreate() {
  if (!newHabitName.value.trim()) return
  await store.addHabit({ name: newHabitName.value.trim() })
  newHabitName.value = ''
}
</script>

<template>
  <div class="home-view">
    <section class="ledger">
      <h2>Mis hábitos</h2>

      <form class="new-entry-form" @submit.prevent="handleCreate">
        <input v-model="newHabitName" placeholder="Nuevo hábito (p. ej. Leer 20 min)" />
        <button type="submit">Añadir</button>
      </form>

      <p v-if="store.loading" class="status-text">Cargando...</p>

      <template v-else>
        <p v-if="store.error" class="status-text status-text--error">{{ store.error }}</p>

        <div v-if="store.habits.length === 0 && !store.error" class="empty-state">
          <p>Aún no hay entradas en tu cuaderno.</p>
          <p class="empty-state__hint">Añade tu primer hábito arriba para empezar a llevar el registro.</p>
        </div>

        <div v-else-if="store.habits.length > 0">
          <HabitCard
            v-for="habit in store.habits"
            :key="habit.id"
            :habit="habit"
            :completed-today="store.completedToday.has(habit.id)"
          />
        </div>
      </template>
    </section>

    <aside>
      <ChatPanel />
    </aside>
  </div>
</template>

<style scoped>
.home-view {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-12);
  align-items: start;
}

@media (max-width: 720px) {
  .home-view {
    grid-template-columns: 1fr;
  }
}

.ledger h2 {
  font-size: 1.3rem;
  margin-bottom: var(--space-6);
}

.new-entry-form {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-6);
}

.new-entry-form input {
  flex: 1;
  border: 1px solid var(--color-stone);
  background: var(--color-paper-raised);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  font-size: 0.95rem;
  color: var(--color-ink);
}

.new-entry-form input::placeholder {
  color: var(--color-ink-soft);
}

.new-entry-form button {
  border: none;
  background: var(--color-ink);
  color: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-4);
  font-size: 0.9rem;
  font-weight: 500;
}

.new-entry-form button:hover {
  background: var(--color-moss);
}

.status-text {
  color: var(--color-ink-soft);
  font-size: 0.9rem;
}

.status-text--error {
  color: var(--color-danger);
}

.empty-state {
  border: 1px dashed var(--color-stone);
  border-radius: 8px;
  padding: var(--space-6);
  color: var(--color-ink-soft);
}

.empty-state p {
  margin: 0;
}

.empty-state__hint {
  margin-top: var(--space-1);
  font-size: 0.9rem;
}
</style>
