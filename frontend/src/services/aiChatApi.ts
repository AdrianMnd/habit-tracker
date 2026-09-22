import type { AiChatRequest } from '@/types/habit'

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

/**
 * Consume el endpoint de streaming (Server-Sent Events) como un async
 * generator, para poder usarlo con "for await...of" en quien lo llame.
 *
 * No usamos el EventSource nativo del navegador porque solo soporta
 * peticiones GET sin cuerpo, y aqui necesitamos enviar el mensaje del
 * usuario en un POST con JSON.
 */
async function* streamChat(data: AiChatRequest): AsyncGenerator<string> {
  const response = await fetch(`${BASE_URL}/ai/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
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

    // Los eventos SSE vienen separados por una linea en blanco.
    // Un chunk de red puede cortar un evento a la mitad, asi que
    // guardamos en el buffer lo que aun no forme un evento completo.
    const events = buffer.split('\n\n')
    buffer = events.pop() ?? ''

    for (const event of events) {
      // Un solo evento SSE puede traer VARIAS lineas "data:" seguidas
      // cuando el valor original contenia saltos de linea (asi lo exige
      // el estandar SSE: cada linea del valor se reenvia con su propio
      // prefijo "data:"). Hay que unirlas todas, no quedarnos solo con
      // la primera, o perdemos texto por el camino.
      const dataLines = event.split('\n').filter((l) => l.startsWith('data:'))
      if (dataLines.length > 0) {
        yield dataLines.map((l) => l.slice('data:'.length).trimStart()).join('\n')
      }
    }
  }
}

export const aiChatApi = {
  streamChat
}
