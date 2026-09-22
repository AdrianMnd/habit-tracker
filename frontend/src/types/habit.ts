export interface Habit {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface HabitRequest {
  name: string
  description?: string
}

export interface HabitLogRequest {
  logDate: string // formato YYYY-MM-DD
  completed: boolean
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
