<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const router = useRouter()

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app">
    <header class="app-header">
      <h1 class="app-header__title">Habit Tracker</h1>

      <nav v-if="authStore.isAuthenticated" class="app-header__nav">
        <RouterLink to="/">Mis hábitos</RouterLink>
        <RouterLink to="/calendar">Calendario</RouterLink>
        <span class="app-header__email">{{ authStore.email }}</span>
        <button type="button" class="logout-button" @click="handleLogout">Cerrar sesión</button>
      </nav>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: var(--space-6) var(--space-8);
  border-bottom: 1px solid var(--color-stone);
}

.app-header__title {
  font-size: 1.5rem;
}

.app-header__nav {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
}

.app-header__nav a {
  font-size: 0.9rem;
  color: var(--color-ink-soft);
}

.app-header__nav a.router-link-active {
  color: var(--color-moss);
}

.app-header__email {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
}

.logout-button {
  border: none;
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 0.85rem;
  padding: 0;
}

.logout-button:hover {
  color: var(--color-danger);
}

.app-main {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-8);
}
</style>
