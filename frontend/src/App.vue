<script setup lang="ts">
import { useAuthStore } from '@/stores/authStore'
import { useThemeStore } from '@/stores/themeStore'
import AppSidebar from '@/components/AppSidebar.vue'
import ChatPanel from '@/components/ChatPanel.vue'

const authStore = useAuthStore()
// No se usa directamente en el template - basta con instanciarlo aqui
// para que aplique el tema guardado nada mas arrancar la app, sin
// esperar a que el usuario visite Ajustes.
useThemeStore()
</script>

<template>
  <div class="app" :class="{ 'app--with-sidebar': authStore.isAuthenticated }">
    <AppSidebar v-if="authStore.isAuthenticated" />

    <div class="app-content">
      <main class="app-main">
        <RouterView />
      </main>

      <!-- Vive aqui, no dentro de una vista concreta, para que la
           conversacion sobreviva al navegar entre paginas y para poder
           anadir un habito recomendado sin importar donde estes. -->
      <aside v-if="authStore.isAuthenticated" class="app-chat">
        <ChatPanel />
      </aside>
    </div>
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

.app-content {
  flex: 1;
  display: flex;
  /* min-width: 0 es necesario porque, por defecto, un item flex nunca se
     encoge por debajo del ancho de su contenido (igual que min-height: 0
     en un flex column, que ya usamos en ChatPanel) - sin esto, un habito
     con un nombre muy largo podria forzar el desbordamiento de toda la
     fila en vez de que el texto se ajuste dentro de su columna. */
  min-width: 0;
  min-height: 100vh;
}

.app-main {
  flex: 1;
  min-width: 0;
  max-width: 1200px;
  padding: var(--space-8);
}

.app-chat {
  /* Antes 360px fijos: en pantallas anchas app-main se queda con su
     max-width: 1200px y sobra ancho de sobra a la derecha que el chat
     no aprovechaba. 460px sigue siendo una columna lateral razonable
     (no un panel principal), pero usa mejor ese espacio libre. */
  width: 460px;
  flex-shrink: 0;
  padding: var(--space-8) var(--space-8) var(--space-8) 0;
}

@media (max-width: 900px) {
  .app-content {
    flex-direction: column;
    min-height: auto;
  }

  .app-chat {
    width: 100%;
    padding: 0 var(--space-4) var(--space-4);
  }
}

@media (max-width: 640px) {
  .app-main {
    padding: var(--space-4);
  }
}
</style>
