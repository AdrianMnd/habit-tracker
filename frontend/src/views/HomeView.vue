<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import HabitCard from '@/components/HabitCard.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import AddHabitModal from '@/components/AddHabitModal.vue'
import StatsSummary from '@/components/StatsSummary.vue'

const store = useHabitStore()
const showModal = ref(false)

onMounted(() => {
  store.fetchHabits()
})
</script>

<template>
  <div>
    <StatsSummary />

    <div class="home-view">
      <section class="ledger panel">
        <div class="ledger__header">
          <h2>Mis hábitos</h2>
          <button type="button" class="add-button" @click="showModal = true">+ Añadir hábito</button>
        </div>

        <p v-if="store.loading" class="status-text">Cargando...</p>

        <template v-else>
          <p v-if="store.error" class="status-text status-text--error">{{ store.error }}</p>

          <div v-if="store.habits.length === 0 && !store.error" class="empty-state">
            <p>Aún no hay entradas en tu cuaderno.</p>
            <p class="empty-state__hint">Añade tu primer hábito para empezar a llevar el registro.</p>
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

    <AddHabitModal v-if="showModal" @close="showModal = false" />
  </div>
</template>

<style scoped>
.home-view {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-12);
  /* Sin "align-items", el valor por defecto de CSS Grid es "stretch":
     cada columna ocupa toda la altura de la fila, no solo la de su propio
     contenido - es lo que permite que el chat pueda crecer tanto como la
     lista de habitos en vez de quedarse en su tamano minimo. */
  min-height: calc(100vh - 220px);
}

@media (max-width: 720px) {
  .home-view {
    grid-template-columns: 1fr;
    /* En movil, apiladas una encima de otra, cada seccion debe medir
       solo lo que necesite - forzar la altura de la ventana aqui dejaria
       un hueco vacio enorme debajo si el chat esta vacio. */
    min-height: auto;
  }
}

.ledger__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-6);
}

.ledger__header h2 {
  font-size: 1.3rem;
}

.add-button {
  border: 1px solid var(--color-moss);
  background: transparent;
  color: var(--color-moss);
  border-radius: 6px;
  padding: var(--space-2) var(--space-4);
  font-size: 0.85rem;
  font-weight: 500;
}

.add-button:hover {
  background: var(--color-moss);
  color: var(--color-paper-raised);
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
