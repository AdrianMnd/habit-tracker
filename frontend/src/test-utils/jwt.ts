// Helper solo para tests: construye un JWT "sintactico" valido (mismo
// formato header.payload.firma que uno real) sin firmar de verdad -
// isTokenExpired() en el frontend solo lee el payload, nunca verifica la
// firma (esa comprobacion solo puede hacerla el backend, que conoce el
// secreto), asi que para probar la logica de expiracion en el cliente
// basta con un payload creible.
function base64url(input: object): string {
  const json = JSON.stringify(input)
  return btoa(json).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function makeToken(expiresInSeconds: number): string {
  const header = base64url({ alg: 'none', typ: 'JWT' })
  const payload = base64url({ sub: 'user@example.com', exp: Math.floor(Date.now() / 1000) + expiresInSeconds })
  return `${header}.${payload}.fake-signature`
}
