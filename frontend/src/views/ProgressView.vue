<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Chart } from 'chart.js/auto'
import { habitApi } from '@/services/habitApi'
import type { HabitStreakSummary, WeeklyProgressPoint } from '@/types/habit'

const progressCanvas = ref<HTMLCanvasElement | null>(null)
const streaksCanvas = ref<HTMLCanvasElement | null>(null)

// Instancias de Chart.js guardadas fuera de cualquier ref: Chart.js
// gestiona su propio estado interno (el contexto del canvas, los
// listeners de eventos del raton para los tooltips...) - no necesitamos
// ni queremos que Vue intente hacerlas reactivas.
let progressChart: Chart | null = null
let streaksChart: Chart | null = null

const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const [progress, streaks] = await Promise.all([
      habitApi.getWeeklyProgress(8),
      habitApi.getStreaks()
    ])
    renderProgressChart(progress)
    renderStreaksChart(streaks)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Error cargando el progreso'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  // Chart.js no se limpia solo cuando Vue desmonta el componente - si no
  // llamamos a destroy() explicitamente, el listener de eventos y el
  // contexto del canvas se quedan vivos en memoria cada vez que entras y
  // sales de esta vista (una fuga de memoria silenciosa que se nota
  // sobre todo tras navegar muchas veces en una sesion larga).
  progressChart?.destroy()
  streaksChart?.destroy()
})

function renderProgressChart(points: WeeklyProgressPoint[]) {
  if (!progressCanvas.value) return

  progressChart = new Chart(progressCanvas.value, {
    type: 'line',
    data: {
      labels: points.map((p) => formatWeekLabel(p.weekStart)),
      datasets: [
        {
          label: 'Cumplimiento semanal medio (%)',
          data: points.map((p) => Math.round(p.averageCompletionRate * 100)),
          borderColor: '#7cb88a',
          backgroundColor: 'rgba(124, 184, 138, 0.15)',
          tension: 0.3,
          fill: true
        }
      ]
    },
    options: chartOptions()
  })
}

function renderStreaksChart(streaks: HabitStreakSummary[]) {
  if (!streaksCanvas.value) return

  streaksChart = new Chart(streaksCanvas.value, {
    type: 'bar',
    data: {
      labels: streaks.map((s) => s.habitName),
      datasets: [
        {
          label: 'Racha actual (días)',
          data: streaks.map((s) => s.currentStreak),
          backgroundColor: '#d9a648'
        }
      ]
    },
    options: chartOptions()
  })
}

function formatWeekLabel(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })
}

// Opciones compartidas: colores a juego con el resto de la app, en vez
// de los grises por defecto de Chart.js que desentonarian con el tema
// oscuro.
function chartOptions() {
  return {
    responsive: true,
    plugins: {
      legend: { labels: { color: '#93a08f' } }
    },
    scales: {
      x: { ticks: { color: '#93a08f' }, grid: { color: '#2a362c' } },
      y: { ticks: { color: '#93a08f' }, grid: { color: '#2a362c' }, beginAtZero: true }
    }
  }
}
</script>

<template>
  <div class="progress-view">
    <p v-if="loading" class="status-text">Cargando...</p>
    <p v-else-if="error" class="status-text status-text--error">{{ error }}</p>

    <template v-else>
      <div class="chart-panel panel">
        <h2>Tendencia de cumplimiento</h2>
        <p class="chart-hint">Media semanal de todos tus hábitos activos, últimas 8 semanas</p>
        <div class="chart-wrapper">
          <canvas ref="progressCanvas"></canvas>
        </div>
      </div>

      <div class="chart-panel panel">
        <h2>Racha actual por hábito</h2>
        <div class="chart-wrapper">
          <canvas ref="streaksCanvas"></canvas>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.progress-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.chart-panel h2 {
  font-size: 1.1rem;
  margin-bottom: var(--space-1);
}

.chart-hint {
  font-size: 0.8rem;
  color: var(--color-ink-soft);
  margin: 0 0 var(--space-4);
}

.chart-wrapper {
  position: relative;
  height: 280px;
}

.status-text {
  font-size: 0.9rem;
  color: var(--color-ink-soft);
}

.status-text--error {
  color: var(--color-danger);
}
</style>
