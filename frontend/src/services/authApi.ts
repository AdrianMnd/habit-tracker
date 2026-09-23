import { apiRequest } from '@/services/http'
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types/auth'

export const authApi = {
  register: (data: RegisterRequest) =>
    apiRequest<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),

  login: (data: LoginRequest) =>
    apiRequest<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(data) })
}
