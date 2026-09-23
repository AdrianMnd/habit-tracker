import { apiRequest } from '@/services/http'
import type { Habit, HabitRequest, HabitLogRequest, HabitsSummary, HabitWeekEntry, Streak } from '@/types/habit'

export const habitApi = {
  getAll: (categoryId?: number) =>
    apiRequest<Habit[]>(categoryId ? `/habits?categoryId=${categoryId}` : '/habits'),

  getById: (id: number) => apiRequest<Habit>(`/habits/${id}`),

  create: (data: HabitRequest) =>
    apiRequest<Habit>('/habits', { method: 'POST', body: JSON.stringify(data) }),

  update: (id: number, data: HabitRequest) =>
    apiRequest<Habit>(`/habits/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  remove: (id: number) => apiRequest<void>(`/habits/${id}`, { method: 'DELETE' }),

  logCompletion: (id: number, data: HabitLogRequest) =>
    apiRequest<void>(`/habits/${id}/logs`, { method: 'POST', body: JSON.stringify(data) }),

  getStreak: (id: number) => apiRequest<Streak>(`/habits/${id}/streak`),

  getWeekView: (start: string) => apiRequest<HabitWeekEntry[]>(`/habits/week?start=${start}`),

  getSummary: () => apiRequest<HabitsSummary>('/habits/summary')
}
