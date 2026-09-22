<script setup lang="ts">
import { ref } from 'vue'
import type { Habit } from '@/types/habit'

defineProps<{
  habit: Habit
}>()

const emit = defineEmits<{
  toggle: [id: number, completed: boolean]
  remove: [id: number]
}>()

const stamped = ref(false)

function handleToggle(id: number) {
  emit('toggle', id, true)
  stamped.value = true
  window.setTimeout(() => (stamped.value = false), 260)
}
</script>

<template>
  <article class="entry">
    <div class="entry__info">
      <RouterLink :to="`/habits/${habit.id}`" class="entry__name">{{ habit.name }}</RouterLink>
      <p v-if="habit.description" class="entry__description">{{ habit.description }}</p>
    </div>

    <div class="entry__actions">
      <button
        type="button"
        class="stamp-button"
        :class="{ 'stamp-button--active': stamped }"
        @click="handleToggle(habit.id)"
      >
        Marcar hoy
      </button>
      <button type="button" class="text-button" @click="emit('remove', habit.id)">Eliminar</button>
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

.stamp-button--active {
  background: var(--color-moss);
  color: var(--color-paper-raised);
  transform: scale(1.06);
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
