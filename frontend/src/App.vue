<script setup lang="ts">
import { useAuthStore } from '@/stores/authStore'
import { useThemeStore } from '@/stores/themeStore'
import AppSidebar from '@/components/AppSidebar.vue'

const authStore = useAuthStore()
// No se usa directamente en el template - basta con instanciarlo aqui
// para que aplique el tema guardado nada mas arrancar la app, sin
// esperar a que el usuario visite Ajustes.
useThemeStore()
</script>

<template>
  <div class="app" :class="{ 'app--with-sidebar': authStore.isAuthenticated }">
    <AppSidebar v-if="authStore.isAuthenticated" />

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app--with-sidebar {
  display: flex;
}

@media (max-width: 768px) {
  .app--with-sidebar {
    /* En movil el sidebar pasa a ser una barra horizontal arriba (ver
       AppSidebar.vue), no una columna al lado - "display: flex" en el
       contenedor padre ya no tiene sentido, volvemos al flujo normal de
       bloques apilados. */
    display: block;
  }
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--space-8);
}

@media (max-width: 640px) {
  .app-main {
    padding: var(--space-4);
  }
}
</style>
