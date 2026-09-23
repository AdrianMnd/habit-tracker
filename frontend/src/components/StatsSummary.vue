<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { habitApi } from '@/services/habitApi'
import type { HabitsSummary } from '@/types/habit'

const summary = ref<HabitsSummary | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    summary.value = await habitApi.getSummary()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-if="!loading && summary" class="stats-row">
    <div class="stat-card panel">
      <span class="stat-card__value">{{ summary.activeHabits }}</span>
      <span class="stat-card__label">Hábitos activos</span>
    </div>

    <div class="stat-card panel">
      <span class="stat-card__value">{{ summary.averageStreak.toFixed(1) }}</span>
      <span class="stat-card__label">Racha media (días)</span>
    </div>

    <div class="stat-card panel">
      <span class="stat-card__value">{{ Math.round(summary.averageWeeklyCompletionRate * 100) }}%</span>
      <span class="stat-card__label">Cumplimiento semanal</span>
    </div>
  </div>
</template>

<style scoped>
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.stat-card {
  display: flex;
  flex-direction: column;
  /* Mismo bug que en AppSidebar.vue: --space-5 no existe, invalidaba
     todo el padding shorthand. De paso, centramos el contenido - un
     numero grande + una etiqueta corta se lee mejor como bloque
     centrado que pegado a la izquierda, es la convencion habitual en
     tarjetas de metricas tipo dashboard. */
  align-items: center;
  text-align: center;
  gap: var(--space-2);
  padding: var(--space-6);
}

.stat-card__value {
  font-family: var(--font-display);
  font-size: 1.8rem;
  color: var(--color-ember);
}

.stat-card__label {
  font-size: 0.8rem;
  color: var(--color-ink-soft);
}

@media (max-width: 640px) {
  .stats-row {
    grid-template-columns: 1fr;
  }
}
</style>
