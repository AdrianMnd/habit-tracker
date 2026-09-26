import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { useHabitChat } from '@/composables/useHabitChat'
import type { ChatStreamEvent } from '@/types/habit'

// vi.hoisted: vi.mock se mueve al principio del archivo, antes que
// cualquier otra linea. Si su factory usara una variable normal declarada
// mas abajo, aun no existiria. vi.hoisted crea la variable tambien "arriba
// del todo", a tiempo para que la factory la pueda usar.
const { mockFetchHabits } = vi.hoisted(() => ({ mockFetchHabits: vi.fn() }))

vi.mock('@/stores/habitStore', () => ({
  useHabitStore: () => ({ fetchHabits: mockFetchHabits })
}))

vi.mock('@/services/aiChatApi', () => ({
  aiChatApi: { streamChat: vi.fn() }
}))

import { aiChatApi } from '@/services/aiChatApi'

function streamOf(...events: ChatStreamEvent[]) {
  return async function* () {
    for (const event of events) yield event
  }
}

describe('useHabitChat', () => {
  beforeEach(() => {
    mockFetchHabits.mockReset()
    vi.mocked(aiChatApi.streamChat).mockReset()
  })

  it('acumula el texto, pinta las sugerencias y refresca la lista si la IA cambia habitos', async () => {
    vi.mocked(aiChatApi.streamChat).mockImplementation(
      streamOf(
        { type: 'suggestions', suggestions: [{ name: 'Beber agua' }] },
        { type: 'text', text: 'Hola ' },
        { type: 'text', text: 'Adrian' },
        { type: 'habits_changed' }
      )
    )
    const { messages, sendMessage } = useHabitChat()

    await sendMessage('hola')

    const assistant = messages.value[1]
    expect(assistant.text).toBe('Hola Adrian')
    expect(assistant.suggestions).toEqual([{ name: 'Beber agua' }])
    expect(mockFetchHabits).toHaveBeenCalledOnce()
  })

  it('un evento error se muestra como error del chat', async () => {
    vi.mocked(aiChatApi.streamChat).mockImplementation(
      streamOf({ type: 'error', message: 'La IA ha tardado demasiado en responder.' })
    )
    const { error, sendMessage } = useHabitChat()

    await sendMessage('hola')

    expect(error.value).toBe('La IA ha tardado demasiado en responder.')
  })

  it('el texto del asistente es reactivo mientras llega el stream (regresion)', async () => {
    // Una "compuerta": el stream no emite nada hasta que el test la abre.
    let openGate!: () => void
    const gate = new Promise<void>((resolve) => (openGate = resolve))
    vi.mocked(aiChatApi.streamChat).mockImplementation(async function* () {
      await gate
      yield { type: 'text', text: 'Hola' }
    })

    const { messages, sendMessage } = useHabitChat()
    const pending = sendMessage('hola')

    // computed() cachea su valor y solo lo recalcula si Vue le avisa de que
    // algo de lo que leyo ha cambiado. Lo leemos ANTES de que llegue el
    // texto, igual que hace la plantilla del chat al pintarse.
    const lastText = computed(() => messages.value[messages.value.length - 1]?.text)
    expect(lastText.value).toBe('')

    openGate()
    await pending

    // Con el bug (mutar el objeto "crudo" en vez del proxy reactivo), Vue
    // nunca avisaba al computed y aqui seguia devolviendo '' en cache.
    expect(lastText.value).toBe('Hola')
  })
})