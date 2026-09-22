<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { habitApi } from '@/services/habitApi'
import type { Habit, Streak } from '@/types/habit'

const props = defineProps<{
  id: string
}>()

const habit = ref<Habit | null>(null)
const streak = ref<Streak | null>(null)

onMounted(async () => {
  const habitId = Number(props.id)
  habit.value = await habitApi.getById(habitId)
  streak.value = await habitApi.getStreak(habitId)
})
</script>

<template>
  <div v-if="habit" class="habit-detail">
    <RouterLink to="/" class="back-link">← Mis hábitos</RouterLink>

    <h2>{{ habit.name }}</h2>
    <p v-if="habit.description" class="description">{{ habit.description }}</p>

    <div v-if="streak" class="streak-row">
      <div class="stamp-badge">
        <span class="stamp-badge__number">{{ streak.currentStreak }}</span>
        <span class="stamp-badge__label">{{ streak.currentStreak === 1 ? 'día' : 'días' }}</span>
      </div>

      <div class="weekly-rate">
        <span class="weekly-rate__value">{{ Math.round(streak.weeklyCompletionRate * 100) }}%</span>
        <span class="weekly-rate__label">cumplimiento esta semana</span>
      </div>
    </div>

    <!-- TODO: calendario de historico de entradas, cuando lo implementemos -->
  </div>
</template>

<style scoped>
.habit-detail {
  max-width: 480px;
}

.back-link {
  display: inline-block;
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin-bottom: var(--space-6);
}

.habit-detail h2 {
  font-size: 1.6rem;
}

.description {
  color: var(--color-ink-soft);
  margin-top: var(--space-2);
}

.streak-row {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  margin-top: var(--space-8);
}

.stamp-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  border: 2px solid var(--color-ember);
  background: var(--color-ember-soft);
  box-shadow: 0 0 24px rgba(217, 166, 72, 0.18);
  flex-shrink: 0;
}

.stamp-badge__number {
  font-family: var(--font-display);
  font-size: 2rem;
  line-height: 1;
  color: var(--color-ink);
}

.stamp-badge__label {
  font-size: 0.75rem;
  color: var(--color-ink-soft);
}

.weekly-rate {
  display: flex;
  flex-direction: column;
}

.weekly-rate__value {
  font-family: var(--font-display);
  font-size: 1.8rem;
  color: var(--color-moss);
}

.weekly-rate__label {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
}
</style>
