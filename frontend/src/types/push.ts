export interface PushConfig {
  enabled: boolean
  publicKey: string | null
}

export interface ReminderSettings {
  hour: number | null // null = recordatorio desactivado
  timeZone: string | null
  subscribedDevices: number
}

/** La forma de PushSubscription.toJSON() - la que espera el backend. */
export interface PushSubscriptionPayload {
  endpoint: string
  keys: { p256dh: string; auth: string }
}