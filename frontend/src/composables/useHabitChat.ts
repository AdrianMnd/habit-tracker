import { ref } from 'vue'
import { aiChatApi } from '@/services/aiChatApi'
import type { ChatMessage, HabitSuggestion } from '@/types/habit'

// Debe coincidir EXACTAMENTE con AiChatService.HABIT_LINE_PREFIX en el backend.
const HABIT_LINE_PREFIX = 'HABITO::'

export function useHabitChat() {
  const messages = ref<ChatMessage[]>([])
  const sending = ref(false)
  const error = ref<string | null>(null)

  async function sendMessage(text: string) {
    const trimmed = text.trim()
    if (!trimmed || sending.value) return

    messages.value.push({ role: 'user', text: trimmed })

    const assistantMessage: ChatMessage = { role: 'assistant', text: '', suggestions: [] }
    messages.value.push(assistantMessage)

    sending.value = true
    error.value = null

    let pendingLine = '' // texto de la linea que aun no ha llegado a un salto de linea
    let visibleText = '' // lineas ya confirmadas como texto normal (no sugerencia)
    const suggestions: HabitSuggestion[] = []

    // Mientras lo que llevamos de la linea actual podria seguir convirtiendose
    // en "HABITO:: ..." (o ya lo es), no la mostramos como texto normal todavia.
    const maybeSuggestionInProgress = () =>
      HABIT_LINE_PREFIX.startsWith(pendingLine) || pendingLine.startsWith(HABIT_LINE_PREFIX)

    function refreshVisibleText() {
      if (maybeSuggestionInProgress()) {
        assistantMessage.text = visibleText
      } else {
        assistantMessage.text = visibleText ? `${visibleText}\n${pendingLine}` : pendingLine
      }
    }

    function commitLine(line: string) {
      const suggestion = parseSuggestionLine(line)
      if (suggestion) {
        suggestions.push(suggestion)
        assistantMessage.suggestions = [...suggestions]
      } else {
        visibleText = visibleText ? `${visibleText}\n${line}` : line
      }
    }

    try {
      for await (const delta of aiChatApi.streamChat({ message: trimmed })) {
        pendingLine += delta

        let newlineIndex = pendingLine.indexOf('\n')
        while (newlineIndex !== -1) {
          commitLine(pendingLine.slice(0, newlineIndex))
          pendingLine = pendingLine.slice(newlineIndex + 1)
          newlineIndex = pendingLine.indexOf('\n')
        }

        refreshVisibleText()
      }

      // Ultima linea, si el mensaje no termino en salto de linea
      if (pendingLine.trim()) {
        commitLine(pendingLine)
        pendingLine = ''
        refreshVisibleText()
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'No se pudo contactar con la IA'
    } finally {
      sending.value = false
    }
  }

  function parseSuggestionLine(line: string): HabitSuggestion | null {
    const trimmed = line.trim()
    if (!trimmed.startsWith(HABIT_LINE_PREFIX)) return null

    const rest = trimmed.slice(HABIT_LINE_PREFIX.length).trim()
    const separatorIndex = rest.indexOf('::')
    if (separatorIndex === -1) return null

    const name = rest.slice(0, separatorIndex).trim()
    const description = rest.slice(separatorIndex + 2).trim()
    if (!name) return null

    return description ? { name, description } : { name }
  }

  return { messages, sending, error, sendMessage }
}
