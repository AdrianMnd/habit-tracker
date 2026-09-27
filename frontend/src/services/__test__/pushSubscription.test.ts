import { describe, expect, it } from 'vitest'
import { isPushSupported, urlBase64ToUint8Array } from '@/services/pushSubscription'

describe('pushSubscription', () => {
  it('convierte una clave base64url (sin relleno, con - y _) a bytes', () => {
    // Bytes elegidos para que su base64 contenga "+" y "/": en base64url
    // pasan a ser "-" y "_", y ademas no lleva el relleno "=".
    const original = new Uint8Array([251, 255, 191, 0, 1, 62])
    const base64Url = btoa(String.fromCharCode(...original))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
    expect(base64Url).toMatch(/[-_]/)

    expect(Array.from(urlBase64ToUint8Array(base64Url))).toEqual(Array.from(original))
  })

  it('detecta que el entorno no soporta push (jsdom no tiene Service Workers)', () => {
    expect(isPushSupported()).toBe(false)
  })
})