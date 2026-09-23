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
  <aside class="sidebar">
    <div class="sidebar__brand">
      <img src="/favicon.svg" alt="" class="sidebar__logo" />
      <h1 class="sidebar__title">Habit Tracker</h1>
    </div>

    <nav class="sidebar__nav">
      <RouterLink to="/">Mis hábitos</RouterLink>
      <RouterLink to="/calendar">Calendario</RouterLink>
      <RouterLink to="/categories">Categorías</RouterLink>
    </nav>

    <div class="sidebar__footer">
      <span class="sidebar__email">{{ authStore.email }}</span>
      <button type="button" class="logout-button" @click="handleLogout">Cerrar sesión</button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-paper-raised);
  border-right: 1px solid var(--color-stone);
  /* --space-5 no existe en nuestra escala (1,2,3,4,6,8,12) - una custom
     property indefinida invalida TODA la propiedad shorthand en la que
     aparece, no solo esa mitad. El padding horizontal no se aplicaba en
     absoluto, de ahi el efecto "pegado al borde". */
  padding: var(--space-8) var(--space-6);
  min-height: 100vh;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
}

.sidebar__logo {
  width: 28px;
  height: 28px;
}

.sidebar__title {
  font-family: var(--font-display);
  font-size: 1.15rem;
  color: var(--color-ink);
  margin: 0;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  flex: 1;
}

.sidebar__nav a {
  font-size: 0.95rem;
  color: var(--color-ink-soft);
  padding: var(--space-2) var(--space-3);
  border-radius: 6px;
}

.sidebar__nav a:hover {
  background: var(--color-moss-soft);
  text-decoration: none;
}

.sidebar__nav a.router-link-active {
  color: var(--color-moss);
  background: var(--color-moss-soft);
  font-weight: 500;
}

.sidebar__footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-top: var(--space-4);
  margin-top: var(--space-4);
  border-top: 1px solid var(--color-stone);
}

.sidebar__email {
  font-size: 0.8rem;
  color: var(--color-ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-button {
  /* Antes: sin borde, sin fondo, sin padding - visualmente era texto
     suelto, no un boton. Un boton necesita un area y un limite propios
     para leerse como "esto se puede pulsar", no solo un color distinto. */
  border: 1px solid var(--color-stone);
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 0.85rem;
  padding: var(--space-2) var(--space-3);
  border-radius: 6px;
  text-align: left;
  cursor: pointer;
}

.logout-button:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

@media (max-width: 768px) {
  .sidebar {
    width: 100%;
    min-height: auto;
    flex-direction: row;
    align-items: center;
    border-right: none;
    border-bottom: 1px solid var(--color-stone);
    padding: var(--space-3) var(--space-4);
    flex-wrap: wrap;
    row-gap: var(--space-3);
  }

  .sidebar__brand {
    margin-bottom: 0;
    margin-right: auto;
  }

  .sidebar__nav {
    flex-direction: row;
    flex: 0;
  }

  .sidebar__footer {
    flex-direction: row;
    align-items: center;
    gap: var(--space-3);
    border-top: none;
    padding-top: 0;
    margin-left: auto;
  }

  .sidebar__email {
    max-width: 120px;
  }
}
</style>
