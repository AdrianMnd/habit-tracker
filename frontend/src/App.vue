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
  /* min-width, no width fijo: app-main es "lo que sobra" despues del
     sidebar y el chat, y ese resto puede llegar a ser muy estrecho en
     anchos intermedios (portatil con el sidebar de 240px y el chat de
     hasta 460px ya restan 700px antes de empezar). 480px es, mas o
     menos, el ancho por debajo del cual el listado de habitos y las
     stat cards dejan de leerse bien (nombres partiendose palabra a
     palabra, tarjetas de stats desbordando su grid) - por debajo de
     eso ya preferimos que el layout entero pase a apilarse en columna
     (ver la media query de mas abajo) antes que seguir comprimiendo. */
  min-width: 480px;
  max-width: 1200px;
  padding: var(--space-8);
}

.app-chat {
  /* clamp(min, preferido, max) en vez de un ancho fijo: en pantallas
     muy anchas se queda en 460px (no tiene sentido que un panel
     lateral crezca sin limite), pero en anchos intermedios se encoge
     con el viewport (26vw) hasta un minimo de 320px - ese encogimiento
     gradual es lo que le devuelve sitio a app-main antes de llegar a
     su min-width y forzar el paso a una sola columna. Con un valor
     fijo de 460px, app-chat nunca cedia nada de ese ancho y era
     siempre app-main quien pagaba el precio. */
  width: clamp(320px, 26vw, 460px);
  flex-shrink: 0;
  padding: var(--space-8) var(--space-8) var(--space-8) 0;
}

/* 1180px, no 900px: con sidebar (240px) + app-main en su minimo
   (480px) + app-chat en su minimo (320px) + el padding de ambos lados,
   la suma minima para que las tres columnas convivan sin comprimirse
   rondaba ~1150-1200px. Con el breakpoint antiguo en 900px, cualquier
   portatil o ventana entre 900 y 1180px caia justo en la zona rota que
   viste en la captura: ni suficientemente ancho para las tres columnas
   ni por debajo del breakpoint que las apila. */
@media (max-width: 1180px) {
  .app-content {
    flex-direction: column;
    min-height: auto;
  }

  .app-main {
    min-width: 0;
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
