import { ref } from 'vue'
import { aiChatApi } from '@/services/aiChatApi'
import type { ChatMessage } from '@/types/habit'

/**
 * Historial de chat en memoria (no se persiste en BD a proposito,
 * ver README/notas del proyecto: se dejo fuera del alcance de la v1).
 */
export function useHabitChat() {
  const messages = ref<ChatMessage[]>([])
  const sending = ref(false)
  const error = ref<string | null>(null)

  async function sendMessage(text: string) {
    const trimmed = text.trim()
    if (!trimmed || sending.value) return

    messages.value.push({ role: 'user', text: trimmed })
    sending.value = true
    error.value = null

    try {
      const response = await aiChatApi.chat({ message: trimmed })
      messages.value.push({
        role: 'assistant',
        text: response.reply,
        suggestions: response.suggestions
      })
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'No se pudo contactar con la IA'
    } finally {
      sending.value = false
    }
  }

  return { messages, sending, error, sendMessage }
}
