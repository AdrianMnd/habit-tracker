/**
 * Fecha LOCAL del usuario en formato YYYY-MM-DD.
 *
 * NO usar date.toISOString().slice(0, 10) para esto: toISOString() convierte
 * a UTC antes de formatear. En Madrid (UTC+2 en verano), el 27/09 a las
 * 00:30 locales son todavia las 22:30 del 26/09 en UTC - asi que marcar un
 * habito "hoy" justo despues de medianoche lo registraba en el dia ANTERIOR.
 *
 * getFullYear/getMonth/getDate, en cambio, devuelven los componentes en la
 * zona horaria local. (getMonth() empieza en 0 - enero es 0 - de ahi el +1.)
 */
export function localIsoDate(date: Date = new Date()): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}