const TOKEN_KEY = 'habit-tracker:token'
const EMAIL_KEY = 'habit-tracker:email'

export const tokenStorage = {
  getToken: (): string | null => localStorage.getItem(TOKEN_KEY),
  getEmail: (): string | null => localStorage.getItem(EMAIL_KEY),

  save(token: string, email: string) {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(EMAIL_KEY, email)
  },

  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EMAIL_KEY)
  }
}
