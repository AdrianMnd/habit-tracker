import { authorizedFetch } from '@/services/http'
import type { AiChatRequest } from '@/types/habit'

/**
 * Consume el endpoint de streaming (Server-Sent Events) como un async
 * generator, para poder usarlo con "for await...of" en quien lo llame.
 *
 * No usamos el EventSource nativo del navegador porque solo soporta
 * peticiones GET sin cuerpo, y aqui necesitamos enviar el mensaje del
 * usuario en un POST con JSON.
 */
async function* streamChat(data: AiChatRequest): AsyncGenerator<string> {
  // Misma renovacion automatica del access token que el resto de la API:
  // antes este archivo duplicaba a mano la cabecera Authorization y el
  // manejo del 401.
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
        // El backend codifica cada fragmento como cadena JSON antes de
        // enviarlo (ver AiChatService.toJsonString). JSON.parse() recupera
        // el texto exacto sin depender de ninguna convencion de espacios
        // de SSE - evita perder o duplicar espacios entre palabras.
        const raw = dataLines.map((l) => l.slice('data:'.length)).join('\n').trim()
        yield JSON.parse(raw) as string
      }
    }
  }
}

export const aiChatApi = {
  streamChat
}
