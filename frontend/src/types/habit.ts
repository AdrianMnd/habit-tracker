import type { Category } from '@/types/category'

export type HabitPriority = 'BAJA' | 'MEDIA' | 'ALTA'

export interface Habit {
  id: number
  name: string
  description: string | null
  priority: HabitPriority
  category: Category | null
  archived: boolean
  createdAt: string
}

export interface HabitRequest {
  name: string
  description?: string
  priority?: HabitPriority
  categoryId?: number
}

export interface HabitLogRequest {
  logDate: string // formato YYYY-MM-DD
  completed: boolean
}

export interface HabitsSummary {
  activeHabits: number
  averageStreak: number
  averageWeeklyCompletionRate: number
}

export interface HabitWeekEntry {
  habitId: number
  habitName: string
  days: Record<string, boolean> // fecha ISO (YYYY-MM-DD) -> cumplido
}

export interface Streak {
  habitId: number
  currentStreak: number
  weeklyCompletionRate: number // 0.0 - 1.0
}

export interface HabitSuggestion {
  name: string
  description?: string
}

export interface AiChatRequest {
  message: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  suggestions?: HabitSuggestion[]
}
