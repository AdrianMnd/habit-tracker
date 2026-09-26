<script setup lang="ts">
import { ref } from 'vue'
import { useHabitChat } from '@/composables/useHabitChat'
import { useHabitStore } from '@/stores/habitStore'
import type { HabitSuggestion } from '@/types/habit'

const { messages, sending, error, sendMessage } = useHabitChat()
const habitStore = useHabitStore()

const draft = ref('')
const addedNames = ref(new Set<string>())

async function handleSend() {
  const text = draft.value
  draft.value = ''
  await sendMessage(text)
}

async function handleAddSuggestion(suggestion: HabitSuggestion) {
  await habitStore.addHabit({ name: suggestion.name, description: suggestion.description ?? undefined })
  addedNames.value.add(suggestion.name)
}
</script>

<template>
  <section class="chat-panel panel">
    <h3>Consulta a la IA</h3>
    <p class="chat-panel__hint">
      Cuéntame un objetivo ("dormir mejor", "ser más productivo") o pregúntame
      sobre tus hábitos actuales.
    </p>

    <div class="chat-log">
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="chat-message"
        :class="`chat-message--${message.role}`"
      >
        <p>{{ message.text }}</p>

        <ul v-if="message.suggestions?.length" class="suggestion-list">
          <li v-for="suggestion in message.suggestions" :key="suggestion.name" class="suggestion">
            <div class="suggestion__info">
              <strong>{{ suggestion.name }}</strong>
              <span v-if="suggestion.description">{{ suggestion.description }}</span>
            </div>
            <button
              type="button"
              class="suggestion__add"
              :disabled="addedNames.has(suggestion.name)"
              @click="handleAddSuggestion(suggestion)"
            >
              {{ addedNames.has(suggestion.name) ? 'Añadido' : 'Añadir' }}
            </button>
          </li>
        </ul>
      </div>

      <p v-if="sending && !messages[messages.length - 1]?.text" class="chat-status">Pensando...</p>
      <p v-if="error" class="chat-status chat-status--error">{{ error }}</p>
    </div>

    <form class="chat-form" @submit.prevent="handleSend">
      <input v-model="draft" placeholder="Escribe tu mensaje..." :disabled="sending" />
      <button type="submit" :disabled="sending || !draft.trim()">Enviar</button>
    </form>
  </section>
</template>

<style scoped>
.chat-panel {
  /* El fondo, borde y radio ya vienen de la clase global "panel" (ver
     main.css) - aqui solo anadimos el layout especifico de este
     componente. */
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  /* Ocupa el 100% de lo que le da el <aside> ya estirado por el grid
     (ver HomeView.vue) - sin esto, un contenedor flex sigue midiendo
     solo lo que necesita su contenido, aunque el padre le de mas sitio. */
  height: 100%;
}

.chat-panel h3 {
  font-size: 1rem;
}

.chat-panel__hint {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0;
}

.chat-log {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  /* flex: 1 hace que este elemento absorba todo el espacio vertical
     sobrante dentro de .margin-note (tras restar el titulo, la pista y
     el formulario). min-height: 0 es el "truco" clasico de flexbox: sin
     el, un hijo flex nunca se encoge por debajo del tamano de su propio
     contenido, así que el overflow-y de abajo nunca llegaria a activarse
     - el navegador preferiria desbordar la pagina entera antes que hacer
     scroll aqui dentro. */
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.chat-message p {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.4;
}

.chat-message--user p {
  color: var(--color-ink-soft);
}

.chat-message--assistant p {
  color: var(--color-ink);
}

.suggestion-list {
  list-style: none;
  margin: var(--space-2) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.suggestion {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  /* El panel que contiene esto ya usa --color-paper-raised como fondo
     (ver .chat-panel); si esta tarjeta usara el mismo tono, se fundiria
     con el panel. Usamos --color-paper (mas oscuro, el tono del
     "escritorio") para que se lea como una superficie hundida dentro
     del panel, no una mas del mismo nivel. */
  background: var(--color-paper);
  border: 1px solid var(--color-stone);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
}

.suggestion__info {
  display: flex;
  flex-direction: column;
  font-size: 0.85rem;
}

.suggestion__info span {
  color: var(--color-ink-soft);
  font-size: 0.8rem;
}

.suggestion__add {
  flex-shrink: 0;
  border: 1px solid var(--color-moss);
  background: transparent;
  color: var(--color-moss);
  border-radius: 999px;
  padding: var(--space-1) var(--space-3);
  font-size: 0.8rem;
}

.suggestion__add:disabled {
  border-color: var(--color-stone);
  color: var(--color-ink-soft);
  cursor: default;
}

.chat-status {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0;
}

.chat-status--error {
  color: var(--color-danger);
}

.chat-form {
  display: flex;
  gap: var(--space-3);
}

.chat-form input {
  flex: 1;
  /* min-width: 0 - el mismo truco de flexbox que ya usamos en
     .app-content y .chat-log: sin esto, un <input> dentro de un
     contenedor flex nunca se encoge por debajo de su ancho "intrinseco"
     (unos 170-200px en la mayoria de navegadores), asi que en una
     .chat-panel estrecha era el input quien empujaba al boton hacia el
     borde derecho en vez de encogerse el primero. */
  min-width: 0;
  border: 1px solid var(--color-stone);
  background: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-ink);
  font-size: 0.85rem;
}

.chat-form button {
  /* flex-shrink: 0 - lo contrario del min-width: 0 de arriba: este si
     queremos que mantenga siempre su tamano completo (texto + padding),
     nunca que se comprima para dejarle sitio al input. */
  flex-shrink: 0;
  white-space: nowrap;
  border: none;
  background: var(--color-ink);
  color: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-6);
  font-size: 0.85rem;
  font-weight: 500;
}

.chat-form button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
