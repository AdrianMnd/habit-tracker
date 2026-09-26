import { beforeEach, describe, expect, it, vi } from 'vitest'
import { aiChatApi, parseSseEvent } from '@/services/aiChatApi'
import type { ChatStreamEvent } from '@/types/habit'

vi.mock('@/services/http', () => ({
  authorizedFetch: vi.fn()
}))

import { authorizedFetch } from '@/services/http'

// Una Response cuyo cuerpo llega en varios trozos, como por la red real.
function streamedResponse(chunks: string[]): Response {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    }
  })
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
}

async function collect(stream: AsyncGenerator<ChatStreamEvent>): Promise<ChatStreamEvent[]> {
  const events: ChatStreamEvent[] = []
  for await (const event of stream) events.push(event)
  return events
}

describe('aiChatApi', () => {
  beforeEach(() => {
    vi.mocked(authorizedFetch).mockReset()
  })

  it('reconstruye eventos que la red ha partido por la mitad', async () => {
    vi.mocked(authorizedFetch).mockResolvedValue(
      streamedResponse([
        // El primer evento llega entero, pero el segundo se corta a mitad
        // de la palabra "text" y termina en el siguiente trozo.
        'event:suggestions\ndata:[{"name":"Beber agua","description":null}]\n\nevent:te',
        'xt\ndata:"Hola "\n\n',
        'event:text\ndata:"Adrian"\n\n'
      ])
    )

    const events = await collect(aiChatApi.streamChat({ message: 'hola' }))

    expect(events).toEqual([
      { type: 'suggestions', suggestions: [{ name: 'Beber agua', description: null }] },
      { type: 'text', text: 'Hola ' },
      { type: 'text', text: 'Adrian' }
    ])
  })

  it('interpreta los eventos habits_changed y error', () => {
    expect(parseSseEvent('event:habits_changed\ndata:{"action":"created"}')).toEqual({ type: 'habits_changed' })
    expect(parseSseEvent('event:error\ndata:{"message":"La IA ha tardado demasiado"}')).toEqual({
      type: 'error',
      message: 'La IA ha tardado demasiado'
    })
  })

  it('ignora eventos de un tipo que no conoce', () => {
    expect(parseSseEvent('event:algo_nuevo\ndata:{}')).toBeNull()
  })
})