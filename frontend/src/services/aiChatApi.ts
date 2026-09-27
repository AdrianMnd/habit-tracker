import { authorizedFetch } from '@/services/http'
import type { AiChatRequest, ChatStreamEvent, HabitSuggestion } from '@/types/habit'

/**
 * Consume el endpoint de streaming (Server-Sent Events) como un async
 * generator, para poder usarlo con "for await...of" en quien lo llame.
 *
 * No usamos el EventSource nativo del navegador porque solo soporta
 * peticiones GET sin cuerpo, y aqui necesitamos enviar el mensaje del
 * usuario en un POST con JSON.
 */
async function* streamChat(data: AiChatRequest): AsyncGenerator<ChatStreamEvent> {
  const response = await authorizedFetch('/ai/chat/stream', {
    method: 'POST',
    body: JSON.stringify(data)
  })

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `Error ${response.status} conectando con la IA`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // Los eventos SSE vienen separados por una linea en blanco. Un trozo
    // de red puede cortar un evento a la mitad (incluso a mitad de la
    // palabra "event:"), asi que lo que aun no forme un evento completo se
    // queda en el buffer esperando al siguiente trozo.
    const rawEvents = buffer.split('\n\n')
    buffer = rawEvents.pop() ?? ''

    for (const rawEvent of rawEvents) {
      const event = parseSseEvent(rawEvent)
      if (event) yield event
    }
  }
}

/**
 * Interpreta UN evento SSE completo:
 *   event:suggestions
 *   data:[{"name":"Beber agua","description":null}]
 *
 * Exportada solo para poder probarla aislada en los tests.
 */
export function parseSseEvent(rawEvent: string): ChatStreamEvent | null {
  // Sin linea "event:", el estandar SSE dice que el evento se llama "message".
  let name = 'message'
  const dataLines: string[] = []

  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      // Un valor con saltos de linea llega repartido en varias lineas
      // "data:" seguidas (asi lo exige el estandar): hay que unirlas todas.
      dataLines.push(line.slice('data:'.length))
    }
  }

  if (dataLines.length === 0) return null

  // El backend codifica SIEMPRE el data como JSON (ver AiChatService.event).
  const payload: unknown = JSON.parse(dataLines.join('\n').trim())

  switch (name) {
    case 'text':
      return { type: 'text', text: payload as string }
    case 'suggestions':
      return { type: 'suggestions', suggestions: payload as HabitSuggestion[] }
    case 'habits_changed':
      return { type: 'habits_changed' }
    case 'error':
      return { type: 'error', message: (payload as { message: string }).message }
    default:
      // Un tipo de evento que esta version del frontend no conoce (p. ej.
      // uno nuevo que añada el backend en el futuro) se ignora en vez de
      // romper el chat: compatibilidad hacia delante.
      return null
  }
}

export const aiChatApi = {
  streamChat
}