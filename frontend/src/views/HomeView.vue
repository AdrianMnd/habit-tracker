<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import { useCategoryStore } from '@/stores/categoryStore'
import HabitCard from '@/components/HabitCard.vue'
import HabitFormModal from '@/components/HabitFormModal.vue'
import StatsSummary from '@/components/StatsSummary.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import type { Habit } from '@/types/habit'

const store = useHabitStore()
const categoryStore = useCategoryStore()
const showModal = ref(false)
const editingHabit = ref<Habit | null>(null)
const categoryFilter = ref<number | ''>('')

function openCreateModal() {
  editingHabit.value = null
  showModal.value = true
}

function openEditModal(habit: Habit) {
  editingHabit.value = habit
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  editingHabit.value = null
}

onMounted(() => {
  store.fetchHabits()
  categoryStore.fetchCategories()
})

watch(categoryFilter, (value) => {
  store.fetchHabits(value || undefined)
})
</script>

<template>
  <div>
    <StatsSummary />

    <section class="ledger panel">
      <div class="ledger__header">
        <h2>Mis hábitos</h2>
        <div class="ledger__header-actions">
          <select v-model="categoryFilter" class="category-filter">
            <option value="">Todas las categorías</option>
            <option v-for="category in categoryStore.categories" :key="category.id" :value="category.id">
              {{ category.name }}
            </option>
          </select>
          <button type="button" class="add-button" @click="openCreateModal">+ Añadir hábito</button>
        </div>
      </div>

      <LoadingSpinner v-if="store.loading" label="Cargando hábitos..." />

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
            @edit="openEditModal"
          />
        </div>
      </template>
    </section>

    <HabitFormModal v-if="showModal" :habit="editingHabit" @close="closeModal" />
  </div>
</template>

<style scoped>
.ledger__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-3);
}

.ledger__header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.category-filter {
  border: 1px solid var(--color-stone);
  background: var(--color-paper);
  color: var(--color-ink-soft);
  border-radius: 6px;
  /* Un <select> nativo (sin appearance: none + flecha propia en SVG)
     dibuja su flecha dentro del padding-right, pero pegada a su borde
     interior - con el mismo padding a los dos lados (--space-3, 12px)
     la flecha quedaba practicamente tocando el borde de la caja. Le
     damos mas aire solo a la derecha (--space-8, 32px) para que la
     flecha tenga hueco donde respirar sin mover el texto de la
     izquierda. */
  padding: var(--space-2) var(--space-8) var(--space-2) var(--space-3);
  font-size: 0.85rem;
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
