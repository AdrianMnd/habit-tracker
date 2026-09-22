import { defineStore } from 'pinia'
import { ref } from 'vue'
import { habitApi } from '@/services/habitApi'
import type { Habit, HabitRequest } from '@/types/habit'

export const useHabitStore = defineStore('habits', () => {
  const habits = ref<Habit[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Ids de habitos marcados como cumplidos hoy (estado local, se resetea
  // al recargar la pagina; el estado real vive en el backend via /logs).
  const completedToday = ref(new Set<number>())

  async function fetchHabits() {
    loading.value = true
    error.value = null
    try {
      habits.value = await habitApi.getAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Error cargando habitos'
    } finally {
      loading.value = false
    }
  }

  async function addHabit(data: HabitRequest) {
    const created = await habitApi.create(data)
    habits.value.push(created)
    return created
  }

  async function removeHabit(id: number) {
    await habitApi.remove(id)
    habits.value = habits.value.filter((h) => h.id !== id)
    completedToday.value.delete(id)
  }

  async function toggleToday(id: number, completed: boolean) {
    const today = new Date().toISOString().slice(0, 10)
    error.value = null
    try {
      await habitApi.logCompletion(id, { logDate: today, completed })
      if (completed) {
        completedToday.value.add(id)
      } else {
        completedToday.value.delete(id)
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'No se pudo marcar el habito'
      throw e
    }
  }

  return { habits, loading, error, completedToday, fetchHabits, addHabit, removeHabit, toggleToday }
})
