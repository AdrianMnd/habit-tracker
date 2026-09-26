const TOKEN_KEY = 'habit-tracker:token'
const REFRESH_TOKEN_KEY = 'habit-tracker:refresh-token'
const EMAIL_KEY = 'habit-tracker:email'

export const tokenStorage = {
  getToken: (): string | null => localStorage.getItem(TOKEN_KEY),
  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  getEmail: (): string | null => localStorage.getItem(EMAIL_KEY),

  save(token: string, refreshToken: string, email: string) {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    localStorage.setItem(EMAIL_KEY, email)
  },

  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(EMAIL_KEY)
  }
}