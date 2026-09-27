export interface RegisterRequest {
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  token: string
  refreshToken: string
  email: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}
