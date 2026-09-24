interface JwtPayload {
  sub?: string
  iat?: number
  exp?: number
}

/**
 * Decodifica el payload de un JWT SIN verificar su firma - eso solo puede
 * hacerlo quien conoce la clave secreta (el backend). Aqui solo queremos
 * leer el campo "exp" para decisiones de UI (mostrar login antes de
 * tiempo), nunca para decisiones de seguridad reales: un token
 * manipulado seguira siendo rechazado por el backend igualmente, esto
 * solo evita la espera inutil de una llamada de red que sabemos que va
 * a fallar.
 */
function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const payloadSegment = token.split('.')[1]
    if (!payloadSegment) return null

    // Un JWT codifica en base64url (usa "-" y "_" en vez de "+" y "/", y
    // no lleva relleno "=") - hay que convertirlo a base64 estandar antes
    // de que atob() lo entienda.
    const base64 = payloadSegment.replace(/-/g, '+').replace(/_/g, '/')

    // atob() por si sola devuelve una "cadena binaria" (un caracter por
    // byte), lo que rompe con cualquier caracter UTF-8 multibyte que
    // pudiera aparecer en el payload. Este patron (decodeURIComponent +
    // escape por codigo de caracter) es el truco estandar para
    // reconstruir el texto UTF-8 real a partir de ahi.
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    )

    return JSON.parse(json) as JwtPayload
  } catch {
    return null
  }
}

/**
 * true si el token ha caducado, es ilegible, o no trae "exp" en absoluto.
 * Ante la duda, lo tratamos como caducado - preferimos pedir login de mas
 * a dejar pasar un token que no podemos confirmar que siga siendo valido.
 */
export function isTokenExpired(token: string): boolean {
  const payload = decodeJwtPayload(token)
  if (!payload?.exp) return true
  return Date.now() >= payload.exp * 1000
}
