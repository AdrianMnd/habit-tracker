import { describe, expect, it } from 'vitest'
import { localIsoDate } from '@/utils/date'

describe('localIsoDate', () => {
  it('devuelve la fecha LOCAL aunque en UTC ya (o aun) sea otro dia', () => {
    // new Date(año, mes, dia, hora...) interpreta los componentes en la
    // zona horaria LOCAL de quien ejecuta el test - asi el test vale igual
    // en tu maquina (Madrid) que en CI (UTC). Mes 8 = septiembre (base 0).
    const justAfterMidnight = new Date(2026, 8, 27, 0, 30)
    const justBeforeMidnight = new Date(2026, 8, 27, 23, 45)

    expect(localIsoDate(justAfterMidnight)).toBe('2026-09-27')
    expect(localIsoDate(justBeforeMidnight)).toBe('2026-09-27')
  })

  it('rellena con ceros el mes y el dia', () => {
    expect(localIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05')
  })
})