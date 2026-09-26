import { ref } from 'vue'
import { aiChatApi } from '@/services/aiChatApi'
import { useHabitStore } from '@/stores/habitStore'
import type { ChatMessage } from '@/types/habit'

export function useHabitChat() {
  const habitStore = useHabitStore()
  const messages = ref<ChatMessage[]>([])
  const sending = ref(false)
  const error = ref<string | null>(null)

  async function sendMessage(text: string) {
    const trimmed = text.trim()
    if (!trimmed || sending.value) return

    messages.value.push({ role: 'user', text: trimmed })
    messages.value.push({ role: 'assistant', text: '', suggestions: [] })

    // OJO: cogemos el mensaje DESPUES de meterlo en el array, leyendolo a
    // traves de messages.value. Asi obtenemos el proxy REACTIVO que Vue
    // envuelve alrededor de cada elemento. La version anterior guardaba el
    // objeto original ("crudo") en una variable antes del push y lo mutaba
    // directamente: esas mutaciones no pasan por el proxy, Vue no se entera
    // y no repinta - el texto no aparecia token a token, sino de golpe al
    // terminar (cuando sending cambiaba y forzaba un repintado).
    const assistantMessage = messages.value[messages.value.length - 1]

    sending.value = true
    error.value = null

    try {
      for await (const event of aiChatApi.streamChat({ message: trimmed })) {
        switch (event.type) {
          case 'text':
            assistantMessage.text += event.text
            break
          case 'suggestions':
            assistantMessage.suggestions = [...(assistantMessage.suggestions ?? []), ...event.suggestions]
            break
          case 'habits_changed':
            // La IA ha creado o archivado un habito: refrescamos la lista
            // para que se vea en la pantalla sin recargar. Sin await: no
            // tiene sentido pausar la lectura del stream mientras tanto.
            void habitStore.fetchHabits()
            break
          case 'error':
            error.value = event.message
            break
          default:
            assertNever(event)
        }
      }
    } catch (e) {
      // Si ya habiamos mostrado texto o sugerencias, el stream fallo al
      // cerrarse (posible corte anomalo de conexion en algun proxy
      // intermedio), no al generar contenido - no tiene sentido asustar
      // al usuario con un error cuando la respuesta ya se ve completa.
      const alreadyHasContent = assistantMessage.text.length > 0 || (assistantMessage.suggestions?.length ?? 0) > 0
      if (!alreadyHasContent) {
        error.value = e instanceof Error ? e.message : 'No se pudo contactar con la IA'
      } else {
        console.warn('El stream de IA termino con un error tras entregar contenido:', e)
      }
    } finally {
      sending.value = false
    }
  }

  return { messages, sending, error, sendMessage }
}

/**
 * Comprobacion de exhaustividad en tiempo de COMPILACION: si mañana se
 * añade un tipo nuevo a ChatStreamEvent y se olvida su "case" en el switch,
 * "event" dejaria de ser de tipo never en el default y TypeScript marcaria
 * error aqui, en vez de ignorar el evento en silencio en produccion.
 */
function assertNever(value: never): never {
  throw new Error(`Evento de chat no contemplado: ${JSON.stringify(value)}`)
}