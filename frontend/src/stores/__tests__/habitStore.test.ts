import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useHabitStore } from '@/stores/habitStore'
import type { Habit } from '@/types/habit'

vi.mock('@/services/habitApi', () => ({
  habitApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    archive: vi.fn(),
    logCompletion: vi.fn()
  }
}))

import { habitApi } from '@/services/habitApi'

function fakeHabit(overrides: Partial<Habit> = {}): Habit {
  return {
    id: 1,
    name: 'Meditar',
    description: null,
    priority: 'MEDIA',
    category: null,
    archived: false,
    createdAt: new Date().toISOString(),
    ...overrides
  }
}

describe('habitStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(habitApi.getAll).mockReset()
    vi.mocked(habitApi.create).mockReset()
    vi.mocked(habitApi.archive).mockReset()
    vi.mocked(habitApi.logCompletion).mockReset()
  })

  it('fetchHabits rellena la lista y limpia loading/error al terminar', async () => {
    vi.mocked(habitApi.getAll).mockResolvedValue([fakeHabit()])

    const store = useHabitStore()
    const promise = store.fetchHabits()
    // Justo tras llamar, loading ya deberia estar activo - si esto
    // fallase, un LoadingSpinner condicionado a store.loading no
    // llegaria a mostrarse nunca durante la carga real.
    expect(store.loading).toBe(true)

    await promise

    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
    expect(store.habits).toHaveLength(1)
    expect(store.habits[0].name).toBe('Meditar')
  })

  it('fetchHabits deja un mensaje de error legible si la API falla, sin lanzar', async () => {
    vi.mocked(habitApi.getAll).mockRejectedValue(new Error('Failed to fetch'))

    const store = useHabitStore()
    await store.fetchHabits()

    expect(store.loading).toBe(false)
    expect(store.error).toBe('Failed to fetch')
    expect(store.habits).toEqual([])
  })

  it('addHabit anade el habito creado a la lista en memoria sin refetch', async () => {
    const created = fakeHabit({ id: 42, name: 'Leer' })
    vi.mocked(habitApi.create).mockResolvedValue(created)

    const store = useHabitStore()
    await store.addHabit({ name: 'Leer', priority: 'MEDIA' })

    expect(store.habits).toHaveLength(1)
    expect(store.habits[0]).toEqual(created)
  })

  it('archiveHabit quita el habito de la lista activa tras confirmar en el backend', async () => {
    vi.mocked(habitApi.getAll).mockResolvedValue([fakeHabit({ id: 1 }), fakeHabit({ id: 2, name: 'Correr' })])
    vi.mocked(habitApi.archive).mockResolvedValue(undefined)

    const store = useHabitStore()
    await store.fetchHabits()
    expect(store.habits).toHaveLength(2)

    await store.archiveHabit(1)

    expect(store.habits).toHaveLength(1)
    expect(store.habits[0].id).toBe(2)
  })

  it('toggleToday marca un habito como completado hoy solo si la llamada al backend tiene exito', async () => {
    const store = useHabitStore()

    vi.mocked(habitApi.logCompletion).mockResolvedValue(undefined)
    await store.toggleToday(1, true)
    expect(store.completedToday.has(1)).toBe(true)

    // Si el backend rechaza la marca (p.ej. 401 por token caducado), el
    // estado local NO debe adelantarse a lo que el servidor confirmo -
    // de lo contrario el usuario veria "Hecho hoy" aunque en realidad
    // no se haya guardado nada.
    vi.mocked(habitApi.logCompletion).mockRejectedValue(new Error('Network error'))
    await expect(store.toggleToday(2, true)).rejects.toThrow()
    expect(store.completedToday.has(2)).toBe(false)
  })
})
