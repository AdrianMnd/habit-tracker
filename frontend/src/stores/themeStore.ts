import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'habit-tracker:theme'

export type Theme = 'dark' | 'light'

function getStoredTheme(): Theme {
  return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark'
}

function applyTheme(value: Theme) {
  // main.css define las variables de color por defecto en :root (tema
  // oscuro) y las sobreescribe en :root[data-theme='light']. Este
  // atributo en <html> es el unico "interruptor" real - toda la app
  // reacciona sola porque ya usa var(--color-x) en vez de colores fijos.
  document.documentElement.setAttribute('data-theme', value)
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<Theme>(getStoredTheme())

  // Se aplica en cuanto el store se crea (la primera vez que algun
  // componente llama a useThemeStore(), normalmente App.vue al arrancar)
  // - asi el atributo esta puesto desde el principio, no solo despues de
  // visitar la pagina de Ajustes.
  applyTheme(theme.value)

  function setTheme(value: Theme) {
    theme.value = value
    localStorage.setItem(STORAGE_KEY, value)
    applyTheme(value)
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, setTheme, toggleTheme }
})
