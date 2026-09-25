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