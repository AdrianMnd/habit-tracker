import { pushApi } from '@/services/pushApi'
import type { PushSubscriptionPayload } from '@/types/push'

/**
 * La parte de Web Push que ocurre EN EL NAVEGADOR: permiso de
 * notificaciones y suscripcion via el Service Worker (PushManager).
 */

export function isPushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

/**
 * La API Push espera la clave VAPID como bytes (Uint8Array), pero la
 * recibimos en base64url (el "base64 apto para URLs": usa - y _ en vez de
 * + y /, y sin relleno =). atob() solo entiende base64 clasico, asi que
 * primero deshacemos esas diferencias.
 */
export function urlBase64ToUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4)
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(base64)
  // Uint8Array<ArrayBuffer> y no un Uint8Array a secas: desde TypeScript 5.7
  // los arrays tipados llevan un generico con el tipo de buffer que hay
  // debajo, y la API Push solo acepta un ArrayBuffer normal (no uno
  // compartido entre hilos, SharedArrayBuffer). "new Uint8Array(n)" crea
  // siempre uno normal, y TypeScript lo sabe.
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

/**
 * navigator.serviceWorker.ready espera PARA SIEMPRE si no hay ningun
 * Service Worker registrado (p. ej. con "npm run dev", donde main.ts no lo
 * registra). Con un timeout damos un error claro en vez de dejar el boton
 * colgado sin explicacion.
 */
async function getRegistration(): Promise<ServiceWorkerRegistration> {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error('No hay Service Worker activo (¿estás en "npm run dev"? Usa "npm run build && npm run preview").')), 5000)
  )
  return Promise.race([navigator.serviceWorker.ready, timeout])
}

export async function getCurrentSubscription(): Promise<PushSubscription | null> {
  if (!isPushSupported()) return null
  const registration = await getRegistration()
  return registration.pushManager.getSubscription()
}

/** Pide permiso, suscribe este navegador y registra la suscripcion en el backend. */
export async function enablePushOnThisDevice(): Promise<void> {
  if (!isPushSupported()) {
    throw new Error('Este navegador no soporta notificaciones push')
  }

  // Tiene que llamarse desde un gesto del usuario (un click): los
  // navegadores bloquean las peticiones de permiso "espontaneas".
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error(
      permission === 'denied'
        ? 'Has bloqueado las notificaciones para esta web. Actívalas desde los ajustes del navegador (icono del candado).'
        : 'No se concedió el permiso de notificaciones'
    )
  }

  const config = await pushApi.config()
  if (!config.enabled || !config.publicKey) {
    throw new Error('Las notificaciones no están configuradas en el servidor')
  }

  const registration = await getRegistration()
  const serverKey = urlBase64ToUint8Array(config.publicKey)

  let subscription = await registration.pushManager.getSubscription()
  // Si ya habia una suscripcion hecha con OTRA clave VAPID (porque se
  // regeneraron las claves del servidor), no vale: hay que tirarla y crear
  // una nueva - subscribe() con una clave distinta lanzaria un error.
  if (subscription && !sameKey(subscription.options.applicationServerKey, serverKey)) {
    await subscription.unsubscribe()
    subscription = null
  }

  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      // userVisibleOnly: true es obligatorio en Chrome - prometemos que cada
      // push mostrara una notificacion visible (nada de "pushes silenciosos"
      // para rastrear al usuario en segundo plano).
      userVisibleOnly: true,
      applicationServerKey: serverKey
    })
  }

  await pushApi.subscribe(subscription.toJSON() as PushSubscriptionPayload)
}

/** Da de baja este navegador, en el backend y en el propio navegador. */
export async function disablePushOnThisDevice(): Promise<void> {
  const subscription = await getCurrentSubscription()
  if (!subscription) return
  await pushApi.unsubscribe(subscription.endpoint)
  await subscription.unsubscribe()
}

/**
 * Al cerrar sesion: se cancela la suscripcion SOLO en el navegador (ya no
 * tenemos token para llamar al backend). El siguiente envio a ese endpoint
 * recibira un 410 del servicio de push y el backend borrara la suscripcion
 * solo (WebPushSender.Result.EXPIRED). Sin esto, en un ordenador compartido
 * el siguiente usuario seguiria recibiendo los recordatorios del anterior.
 */
export async function unsubscribeThisBrowserOnly(): Promise<void> {
  if (!isPushSupported()) return
  const registration = await navigator.serviceWorker.getRegistration()
  const subscription = await registration?.pushManager.getSubscription()
  await subscription?.unsubscribe()
}

function sameKey(current: ArrayBuffer | null, expected: Uint8Array): boolean {
  if (!current) return false
  const bytes = new Uint8Array(current)
  return bytes.length === expected.length && bytes.every((byte, i) => byte === expected[i])
}