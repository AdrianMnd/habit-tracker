<script setup lang="ts">
import { ref } from 'vue'
import { useHabitStore } from '@/stores/habitStore'
import type { Habit } from '@/types/habit'

const props = defineProps<{
  habit: Habit
  completedToday: boolean
}>()

const habitStore = useHabitStore()
const pulsing = ref(false)
const pending = ref(false)

async function handleToggle() {
  if (pending.value) return
  pending.value = true
  pulsing.value = true
  try {
    await habitStore.toggleToday(props.habit.id, !props.completedToday)
  } catch {
    // el error ya queda expuesto en habitStore.error para que la vista lo muestre
  } finally {
    pending.value = false
    window.setTimeout(() => (pulsing.value = false), 260)
  }
}

async function handleRemove() {
  await habitStore.removeHabit(props.habit.id)
}
</script>

<template>
  <article class="entry">
    <div class="entry__info">
      <div class="entry__name-row">
        <span
          class="priority-dot"
          :class="`priority-dot--${(habit.priority ?? 'MEDIA').toLowerCase()}`"
          :title="`Prioridad ${(habit.priority ?? 'MEDIA').toLowerCase()}`"
        />
        <RouterLink :to="`/habits/${habit.id}`" class="entry__name">{{ habit.name }}</RouterLink>
      </div>
      <p v-if="habit.description" class="entry__description">{{ habit.description }}</p>
    </div>

    <div class="entry__actions">
      <button
        type="button"
        class="stamp-button"
        :class="{ 'stamp-button--done': completedToday, 'stamp-button--pulse': pulsing }"
        :disabled="pending"
        @click="handleToggle"
      >
        {{ completedToday ? 'Hecho hoy ✓' : 'Marcar hoy' }}
      </button>
      <button type="button" class="text-button" @click="handleRemove">Eliminar</button>
    </div>
  </article>
</template>

<style scoped>
.entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--color-stone);
}

.entry__name-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.priority-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.priority-dot--alta {
  background: var(--color-danger);
}

.priority-dot--media {
  background: var(--color-ember);
}

.priority-dot--baja {
  background: var(--color-ink-soft);
}

.entry__name {
  font-family: var(--font-display);
  font-size: 1.15rem;
  color: var(--color-ink);
}

.entry__name:hover {
  color: var(--color-moss);
}

.entry__description {
  margin: var(--space-1) 0 0;
  font-size: 0.9rem;
  color: var(--color-ink-soft);
}

.entry__actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.stamp-button {
  border: 1px solid var(--color-moss);
  background: transparent;
  color: var(--color-moss);
  padding: var(--space-2) var(--space-4);
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 500;
  transition: transform 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.stamp-button--done {
  background: var(--color-moss);
  color: var(--color-paper-raised);
}

.stamp-button--pulse {
  transform: scale(1.06);
}

.stamp-button:disabled {
  opacity: 0.7;
  cursor: default;
}

.text-button {
  border: none;
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 0.85rem;
  padding: var(--space-2) var(--space-3);
}

.text-button:hover {
  color: var(--color-danger);
}
</style>
