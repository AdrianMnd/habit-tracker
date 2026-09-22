import { apiRequest } from '@/services/http'
import type { AiChatRequest, AiChatResponse } from '@/types/habit'

export const aiChatApi = {
  chat: (data: AiChatRequest) =>
    apiRequest<AiChatResponse>('/ai/chat', { method: 'POST', body: JSON.stringify(data) })
}
