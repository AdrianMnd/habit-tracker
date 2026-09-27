import { ref } from 'vue'
import { pushApi } from '@/services/pushApi'
import {
  disablePushOnThisDevice,
  enablePushOnThisDevice,
  getCurrentSubscription,
  isPushSupported
} from '@/services/pushSubscription'

export function usePushReminders() {
  const supported = isPushSupported()
  // Zona horaria IANA del sistema ("Europe/Madrid"): la que usara el
  // backend para saber cuando son "tus" 21:00.
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone

  const loading = ref(true)
  const busy = ref(false)
  const serverEnabled = ref(false)
  const deviceSubscribed = ref(false)
  const hour = ref<number | null>(null)
  const subscribedDevices = ref(0)
  const error = ref<string | null>(null)
  const info = ref<string | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      // Promise.all: las dos peticiones salen a la vez, no una tras otra.
      const [config, settings] = await Promise.all([pushApi.config(), pushApi.getReminder()])
      serverEnabled.value = config.enabled
      hour.value = settings.hour
      subscribedDevices.value = settings.subscribedDevices
      deviceSubscribed.value =
        supported && Notification.permission === 'granted' && (await getCurrentSubscription().catch(() => null)) !== null
    } catch (e) {
      error.value = messageOf(e)
    } finally {
      loading.value = false
    }
  }

  async function setDeviceEnabled(enabled: boolean) {
    await run(async () => {
      if (enabled) {
        await enablePushOnThisDevice()
        info.value =
          hour.value === null
            ? 'Notificaciones activadas. Elige a qué hora quieres el recordatorio.'
            : 'Notificaciones activadas en este dispositivo.'
      } else {
        await disablePushOnThisDevice()
        info.value = 'Notificaciones desactivadas en este dispositivo.'
      }
      deviceSubscribed.value = enabled
      subscribedDevices.value = (await pushApi.getReminder()).subscribedDevices
    })
  }

  async function setHour(newHour: number | null) {
    await run(async () => {
      const settings = await pushApi.updateReminder(newHour, timeZone)
      hour.value = settings.hour
      info.value =
        settings.hour === null
          ? 'Recordatorio desactivado.'
          : `Te avisaremos a partir de las ${formatHour(settings.hour)} si te quedan hábitos por marcar.`
    })
  }

  async function sendTest() {
    await run(async () => {
      const { delivered } = await pushApi.sendTest()
      info.value =
        delivered > 0
          ? `Notificación de prueba enviada a ${delivered} dispositivo${delivered === 1 ? '' : 's'}.`
          : 'No se pudo entregar a ningún dispositivo. Prueba a desactivar y volver a activar las notificaciones.'
    })
  }

  // Envuelve cada accion con el mismo manejo de estado: bloquear los
  // controles mientras dura, y limpiar/rellenar los mensajes.
  async function run(action: () => Promise<void>) {
    busy.value = true
    error.value = null
    info.value = null
    try {
      await action()
    } catch (e) {
      error.value = messageOf(e)
    } finally {
      busy.value = false
    }
  }

  return {
    supported,
    timeZone,
    loading,
    busy,
    serverEnabled,
    deviceSubscribed,
    hour,
    subscribedDevices,
    error,
    info,
    load,
    setDeviceEnabled,
    setHour,
    sendTest
  }
}

export function formatHour(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : 'Algo ha ido mal'
}