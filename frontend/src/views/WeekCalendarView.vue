<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { habitApi } from '@/services/habitApi'
import type { HabitWeekEntry } from '@/types/habit'

const DAY_LABELS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']

function mondayOf(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay() // 0 = domingo, 1 = lunes...
  const diff = (day === 0 ? -6 : 1) - day
  d.setDate(d.getDate() + diff)
  d.setHours(0, 0, 0, 0)
  return d
}

function toISODate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function isToday(date: Date): boolean {
  return toISODate(date) === toISODate(new Date())
}

const weekStart = ref(mondayOf(new Date()))
const entries = ref<HabitWeekEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const pendingCell = ref<string | null>(null)

const weekDays = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart.value)
    d.setDate(d.getDate() + i)
    return d
  })
)

const weekRangeLabel = computed(() => {
  const first = weekDays.value[0].toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })
  const last = weekDays.value[6].toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })
  return `${first} – ${last}`
})

async function fetchWeek() {
  loading.value = true
  error.value = null
  try {
    entries.value = await habitApi.getWeekView(toISODate(weekStart.value))
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Error cargando el calendario'
  } finally {
    loading.value = false
  }
}

function changeWeek(deltaDays: number) {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + deltaDays)
  weekStart.value = d
}

async function toggleDay(entry: HabitWeekEntry, date: Date) {
  const iso = toISODate(date)
  const cellKey = `${entry.habitId}-${iso}`
  if (pendingCell.value === cellKey) return

  const currentlyCompleted = entry.days[iso] ?? false
  pendingCell.value = cellKey

  try {
    await habitApi.logCompletion(entry.habitId, { logDate: iso, completed: !currentlyCompleted })
    entry.days[iso] = !currentlyCompleted
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'No se pudo actualizar ese día'
  } finally {
    pendingCell.value = null
  }
}

onMounted(fetchWeek)
watch(weekStart, fetchWeek)
</script>

<template>
  <div class="week-view">
    <div class="week-view__header">
      <h2>Calendario semanal</h2>
      <div class="week-nav">
        <button type="button" @click="changeWeek(-7)">← Semana anterior</button>
        <span class="week-range">{{ weekRangeLabel }}</span>
        <button type="button" @click="changeWeek(7)">Semana siguiente →</button>
      </div>
    </div>

    <p v-if="loading" class="status-text">Cargando...</p>
    <p v-else-if="error" class="status-text status-text--error">{{ error }}</p>

    <div v-else-if="entries.length === 0" class="empty-state">
      <p>Aún no tienes hábitos que mostrar aquí. Añade alguno desde "Mis hábitos".</p>
    </div>

    <table v-else class="week-table">
      <thead>
        <tr>
          <th class="week-table__habit-col">Hábito</th>
          <th v-for="(day, i) in weekDays" :key="i" :class="{ 'is-today': isToday(day) }">
            {{ DAY_LABELS[i] }}
            <span class="day-number">{{ day.getDate() }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entry in entries" :key="entry.habitId">
          <td class="week-table__habit-col">{{ entry.habitName }}</td>
          <td v-for="(day, i) in weekDays" :key="i" :class="{ 'is-today': isToday(day) }">
            <button
              type="button"
              class="day-cell"
              :class="{ 'day-cell--done': entry.days[toISODate(day)] }"
              :disabled="pendingCell === `${entry.habitId}-${toISODate(day)}`"
              :aria-label="`Marcar ${entry.habitName} el ${toISODate(day)}`"
              @click="toggleDay(entry, day)"
            >
              <span v-if="entry.days[toISODate(day)]">✓</span>
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.week-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.week-view__header {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.week-nav {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.week-nav button {
  border: 1px solid var(--color-stone);
  background: var(--color-paper-raised);
  color: var(--color-ink-soft);
  border-radius: 6px;
  padding: var(--space-1) var(--space-3);
  font-size: 0.85rem;
}

.week-nav button:hover {
  color: var(--color-moss);
  border-color: var(--color-moss);
}

.week-range {
  font-size: 0.9rem;
  color: var(--color-ink-soft);
  min-width: 130px;
  text-align: center;
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

.week-table {
  border-collapse: collapse;
  width: 100%;
}

.week-table th,
.week-table td {
  padding: var(--space-2);
  text-align: center;
  border-bottom: 1px solid var(--color-stone);
  font-size: 0.8rem;
  color: var(--color-ink-soft);
}

.week-table__habit-col {
  text-align: left;
  font-family: var(--font-display);
  font-size: 0.95rem;
  color: var(--color-ink);
  min-width: 160px;
}

.day-number {
  display: block;
  font-size: 0.72rem;
}

th.is-today,
td.is-today {
  background: var(--color-moss-soft);
}

.day-cell {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--color-stone);
  background: transparent;
  color: var(--color-moss);
  font-size: 0.85rem;
  cursor: pointer;
}

.day-cell:hover:not(:disabled) {
  border-color: var(--color-moss);
}

.day-cell--done {
  background: var(--color-moss);
  border-color: var(--color-moss);
  color: var(--color-paper-raised);
}

.day-cell:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
