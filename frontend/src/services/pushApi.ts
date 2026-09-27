import { apiRequest } from '@/services/http'
import type { PushConfig, PushSubscriptionPayload, ReminderSettings } from '@/types/push'

export const pushApi = {
  config: () => apiRequest<PushConfig>('/push/config'),

  subscribe: (subscription: PushSubscriptionPayload) =>
    apiRequest<void>('/push/subscriptions', { method: 'POST', body: JSON.stringify(subscription) }),

  unsubscribe: (endpoint: string) =>
    apiRequest<void>('/push/subscriptions', { method: 'DELETE', body: JSON.stringify({ endpoint }) }),

  getReminder: () => apiRequest<ReminderSettings>('/push/reminder'),

  updateReminder: (hour: number | null, timeZone: string) =>
    apiRequest<ReminderSettings>('/push/reminder', { method: 'PUT', body: JSON.stringify({ hour, timeZone }) }),

  sendTest: () => apiRequest<{ delivered: number }>('/push/test', { method: 'POST' })
}