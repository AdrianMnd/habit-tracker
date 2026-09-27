import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePushReminders } from '@/composables/usePushReminders'

vi.mock('@/services/pushApi', () => ({
  pushApi: {
    config: vi.fn(),
    getReminder: vi.fn(),
    updateReminder: vi.fn(),
    sendTest: vi.fn()
  }
}))

vi.mock('@/services/pushSubscription', () => ({
  isPushSupported: () => true,
  getCurrentSubscription: vi.fn(),
  enablePushOnThisDevice: vi.fn(),
  disablePushOnThisDevice: vi.fn()
}))

import { pushApi } from '@/services/pushApi'
import { enablePushOnThisDevice } from '@/services/pushSubscription'

describe('usePushReminders', () => {
  beforeEach(() => {
    vi.mocked(pushApi.updateReminder).mockReset()
    vi.mocked(pushApi.sendTest).mockReset()
    vi.mocked(enablePushOnThisDevice).mockReset()
  })

  it('al elegir hora envia tambien la zona horaria del navegador', async () => {
    vi.mocked(pushApi.updateReminder).mockResolvedValue({ hour: 21, timeZone: 'Europe/Madrid', subscribedDevices: 1 })
    const { setHour, hour, info, timeZone } = usePushReminders()

    await setHour(21)

    expect(pushApi.updateReminder).toHaveBeenCalledWith(21, timeZone)
    expect(hour.value).toBe(21)
    expect(info.value).toContain('21:00')
  })

  it('si el usuario deniega el permiso, se muestra el motivo y no queda como activado', async () => {
    vi.mocked(enablePushOnThisDevice).mockRejectedValue(new Error('Has bloqueado las notificaciones para esta web.'))
    const { setDeviceEnabled, deviceSubscribed, error, busy } = usePushReminders()

    await setDeviceEnabled(true)

    expect(deviceSubscribed.value).toBe(false)
    expect(error.value).toContain('bloqueado')
    expect(busy.value).toBe(false) // los controles se desbloquean aunque falle
  })

  it('avisa si la notificacion de prueba no llego a ningun dispositivo', async () => {
    vi.mocked(pushApi.sendTest).mockResolvedValue({ delivered: 0 })
    const { sendTest, info } = usePushReminders()

    await sendTest()

    expect(info.value).toContain('No se pudo entregar')
  })
})