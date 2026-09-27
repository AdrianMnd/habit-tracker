const CACHE_NAME = 'habit-tracker-v1'

const APP_SHELL = [
  '/',
  '/manifest.json',
  '/favicon.svg'
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(APP_SHELL)
    })
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((nombresDeCache) => {
      return Promise.all(
        nombresDeCache
          .filter((nombre) => nombre !== CACHE_NAME)
          .map((nombre) => caches.delete(nombre))
      )
    })
  )
})

self.addEventListener('fetch', (event) => {
  if ( event.request.method !== 'GET' || event.request.url.includes('/api/')) {
    return
  }
  event.respondWith(
    caches.match(event.request).then((respuestaCacheada) => {
        return respuestaCacheada || fetch(event.request)
    })
  )
})

// --- Web Push ---

// Llega un push del servidor (aunque la app este cerrada: el navegador
// despierta al Service Worker solo para esto). event.waitUntil mantiene al
// SW vivo hasta que la notificacion se haya mostrado - sin el, el navegador
// podria "dormirlo" a mitad.
self.addEventListener('push', (event) => {
  let data = { title: 'Habit Tracker', body: '', url: '/' }
  try {
    data = { ...data, ...event.data.json() }
  } catch {
    if (event.data) data.body = event.data.text()
  }

  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: '/icons/icon-192.png',
      // tag: un recordatorio nuevo SUSTITUYE al anterior en vez de apilarse.
      tag: 'daily-reminder',
      data: { url: data.url }
    })
  )
})

// Click en la notificacion: si la app ya esta abierta en alguna pestaña, la
// traemos al frente; si no, abrimos una nueva.
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.url ?? '/', self.location.origin).href

  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      const existing = windows.find((client) => client.url.startsWith(self.location.origin))
      if (existing) {
        return existing.focus()
      }
      return self.clients.openWindow(url)
    })()
  )
})